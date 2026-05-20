package com.github.pablolec.play1toolkit.playjpa.references

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

class PlayYamlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement().withLanguage(YAMLLanguage.INSTANCE),
            PlayYamlReferenceProvider(),
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}

private class PlayYamlReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val yamlFile = element.containingFile as? YAMLFile ?: return PsiReference.EMPTY_ARRAY
        val vf = yamlFile.virtualFile ?: return PsiReference.EMPTY_ARRAY
        if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return PsiReference.EMPTY_ARRAY
        if (!PlayYamlFixtureUtils.looksLikeFixtureFile(yamlFile)) return PsiReference.EMPTY_ARRAY

        val kv = element as? YAMLKeyValue ?: (element.parent as? YAMLKeyValue) ?: return PsiReference.EMPTY_ARRAY
        val project = element.project
        val svc = PlayJpaModelService.getInstance(project)
        val refs = mutableListOf<PsiReference>()

        // Top-level key: "Post(alias)" → reference from "Post" to PsiClass
        val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping
        if (kv.parent == topMapping) {
            val keyText = kv.keyText
            val parsed = PlayYamlFixtureUtils.parseFixtureKey(keyText)
            if (parsed != null) {
                val (className, _) = parsed
                val keyEl = kv.key ?: return PsiReference.EMPTY_ARRAY
                val classRange = TextRange(0, className.length)
                refs.add(PlayYamlModelClassReference(keyEl, classRange, className, project))
            }
        } else {
            // Child key: field name
            val parentKv = kv.parent?.parent as? YAMLKeyValue
            if (parentKv != null && parentKv.parent == topMapping) {
                val modelName = PlayYamlFixtureUtils.getModelNameFromKey(parentKv) ?: return PsiReference.EMPTY_ARRAY
                val model = svc.findModelByName(modelName) ?: return PsiReference.EMPTY_ARRAY
                val fieldName = kv.keyText
                val keyEl = kv.key ?: return PsiReference.EMPTY_ARRAY
                refs.add(PlayYamlFieldReference(keyEl, TextRange(0, keyEl.textLength), model.psiClass, fieldName))

                // Value: if this field is a relation → reference to alias
                val valueEl = kv.value as? YAMLScalar
                if (valueEl != null) {
                    val relation = model.relations.firstOrNull { it.fieldName == fieldName }
                    if (relation != null) {
                        val aliasText = valueEl.textValue
                        refs.add(PlayYamlAliasReference(valueEl, TextRange(0, valueEl.textLength), relation.targetModel ?: "", aliasText, yamlFile))
                    }
                }
            }
        }

        return refs.toTypedArray()
    }
}

class PlayYamlModelClassReference(
    element: PsiElement,
    range: TextRange,
    private val className: String,
    private val project: com.intellij.openapi.project.Project
) : PsiReferenceBase<PsiElement>(element, range) {
    override fun resolve(): PsiElement? {
        val svc = PlayJpaModelService.getInstance(project)
        return svc.findModelByName(className)?.psiClass
    }
    override fun getVariants(): Array<Any> = emptyArray()
    override fun handleElementRename(newElementName: String): PsiElement =
        ElementManipulators.handleContentChange(element, rangeInElement, newElementName)
}

class PlayYamlFieldReference(
    element: PsiElement,
    range: TextRange,
    private val modelClass: PsiClass,
    private val fieldName: String
) : PsiReferenceBase<PsiElement>(element, range) {
    override fun resolve(): PsiElement? = modelClass.findFieldByName(fieldName, true)
    override fun getVariants(): Array<Any> = emptyArray()
    override fun handleElementRename(newElementName: String): PsiElement =
        ElementManipulators.handleContentChange(element, rangeInElement, newElementName)
}

class PlayYamlAliasReference(
    element: PsiElement,
    range: TextRange,
    private val targetModelName: String,
    private val aliasName: String,
    private val yamlFile: YAMLFile
) : PsiReferenceBase<PsiElement>(element, range) {
    override fun resolve(): PsiElement? {
        val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return null
        return topMapping.keyValues.firstOrNull { kv ->
            val parsed = PlayYamlFixtureUtils.parseFixtureKey(kv.keyText) ?: return@firstOrNull false
            parsed.first == targetModelName && parsed.second == aliasName
        }
    }
    override fun getVariants(): Array<Any> = emptyArray()
}

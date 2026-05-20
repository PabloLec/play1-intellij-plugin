package com.github.pablolec.play1toolkit.playjpa.completion

import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaFieldInfo
import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

class PlayYamlFieldCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(YAMLLanguage.INSTANCE),
            PlayYamlFieldCompletionProvider()
        )
    }
}

private class PlayYamlFieldCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val file = parameters.originalFile as? YAMLFile ?: return
        val vf = file.virtualFile ?: return
        if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return
        val element = parameters.position
        val topMapping = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return

        // Find the parent model block
        val parentKv = generateSequence(element.parent) { it.parent }
            .filterIsInstance<YAMLKeyValue>()
            .firstOrNull { it.parent == topMapping } ?: return
        val modelName = PlayYamlFixtureUtils.getModelNameFromKey(parentKv) ?: return
        val svc = PlayJpaModelService.getInstance(parameters.position.project)
        val model = svc.findModelByName(modelName) ?: return

        val allFields = model.fields + (model.idField?.let { listOf(it) } ?: emptyList()) +
            model.relations.map { PlayJpaFieldInfo(it.fieldName, it.targetModel ?: "Object", it.psiField, listOf(it.relationKind.name)) }

        for (field in allFields) {
            result.addElement(
                LookupElementBuilder.create(field.name)
                    .withTypeText(field.typeText)
            )
        }
    }
}

package com.github.pablolec.play1toolkit.playjpa.completion

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

class PlayYamlRelationTargetCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(YAMLLanguage.INSTANCE),
            PlayYamlRelationTargetCompletionProvider()
        )
    }
}

private class PlayYamlRelationTargetCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val file = parameters.originalFile as? YAMLFile ?: return
        val vf = file.virtualFile ?: return
        if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return
        val element = parameters.position
        val topMapping = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return

        val fieldKv = element.parent as? YAMLKeyValue ?: return
        val parentBlockKv = fieldKv.parent?.parent as? YAMLKeyValue ?: return
        if (parentBlockKv.parent != topMapping) return

        val modelName = PlayYamlFixtureUtils.getModelNameFromKey(parentBlockKv) ?: return
        val svc = PlayJpaModelService.getInstance(parameters.position.project)
        val model = svc.findModelByName(modelName) ?: return
        val fieldName = fieldKv.keyText
        val relation = model.relations.firstOrNull { it.fieldName == fieldName } ?: return
        val targetModel = relation.targetModel ?: return

        val aliases = PlayYamlFixtureUtils.getAllAliasesForModel(file, targetModel)
        for (alias in aliases) {
            result.addElement(LookupElementBuilder.create(alias).withTypeText("$targetModel fixture"))
        }
    }
}

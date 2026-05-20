package com.github.pablolec.play1toolkit.playjpa.completion

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

class PlayYamlModelCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(YAMLLanguage.INSTANCE),
            PlayYamlModelCompletionProvider()
        )
    }
}

private class PlayYamlModelCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val file = parameters.originalFile as? YAMLFile ?: return
        val vf = file.virtualFile ?: return
        if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return
        val element = parameters.position
        val topMapping = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return
        val kv = element.parent?.parent
        if (kv != topMapping && element.parent?.parent?.parent != topMapping) return

        val svc = PlayJpaModelService.getInstance(parameters.position.project)
        for (model in svc.getAllModels()) {
            result.addElement(
                LookupElementBuilder.create("${model.className}(")
                    .withPresentableText(model.className)
                    .withTailText("(alias)")
                    .withTypeText("Play JPA model")
            )
        }
    }
}

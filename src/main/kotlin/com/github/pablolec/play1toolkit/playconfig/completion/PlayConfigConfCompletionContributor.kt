package com.github.pablolec.play1toolkit.playconfig.completion

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigTokenTypes
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigKnownKeys
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class PlayConfigConfCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PlayConfigTokenTypes.KEY)
                .withLanguage(PlayConfigLanguage),
            PlayConfigConfCompletionProvider()
        )
    }
}

private class PlayConfigConfCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val project = parameters.position.project
        if (DumbService.isDumb(project)) return

        // Suggest known Play framework keys
        PlayConfigKnownKeys.allKnownKeys().forEach { key ->
            result.addElement(
                LookupElementBuilder.create(key)
                    .withTypeText("Play 1 framework key")
                    .withCaseSensitivity(false)
            )
        }

        // Suggest existing project keys (for profile variants)
        val svc = PlayConfigService.getInstance(project)
        val existingKeys = svc.allKeys().map { it.logicalKey }.distinct()
        val profiles = svc.availableProfiles()

        existingKeys.forEach { key ->
            profiles.forEach { profile ->
                result.addElement(
                    LookupElementBuilder.create("%$profile.$key")
                        .withTypeText("override for profile: $profile")
                        .withCaseSensitivity(false)
                )
            }
        }
    }
}

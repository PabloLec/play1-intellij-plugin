package com.github.pablolec.play1toolkit.playmessages.inspections

import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesTokenTypes
import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesFile
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElementVisitor

class MissingLocaleTranslationInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : com.intellij.psi.PsiElementVisitor() {
            override fun visitFile(file: com.intellij.psi.PsiFile) {
                if (DumbService.isDumb(file.project)) return
                val messagesFile = file as? PlayMessagesFile ?: return
                val svc = PlayMessagesService.getInstance(file.project)
                val availableLocales = svc.localesAvailable()
                if (availableLocales.isEmpty()) return

                // Build a map of key → set of locales present (for all files)
                val keyToLocales = mutableMapOf<String, MutableSet<String?>>()
                svc.allEntries().forEach { entry ->
                    keyToLocales.getOrPut(entry.key) { mutableSetOf() }.add(entry.locale)
                }

                for (prop in messagesFile.getProperties()) {
                    val key = prop.key
                    if (key.isBlank()) continue
                    val localesPresent = keyToLocales[key] ?: continue

                    // Check if this file's locale has the key but other locales are missing it
                    val missingLocales = (availableLocales.toSet() + setOf<String?>(null))
                        .filter { it !in localesPresent }
                    if (missingLocales.isNotEmpty()) {
                        val keyElement = prop.node.findChildByType(PlayMessagesTokenTypes.KEY)?.psi ?: prop
                        val missingDesc = missingLocales.joinToString(", ") { it ?: "default" }
                        holder.registerProblem(keyElement, "Key '$key' has no translation for: $missingDesc")
                    }
                }
            }
        }
    }
}

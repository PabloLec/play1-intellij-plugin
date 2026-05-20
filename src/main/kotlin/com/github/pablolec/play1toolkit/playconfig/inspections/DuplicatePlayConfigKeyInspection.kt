package com.github.pablolec.play1toolkit.playconfig.inspections

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigLanguage
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigFile
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.intellij.codeInspection.*
import com.intellij.openapi.project.DumbService
import com.intellij.psi.*

class DuplicatePlayConfigKeyInspection : LocalInspectionTool() {
    override fun getDisplayName() = "Duplicate Play configuration key"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "DuplicatePlayConfigKey"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (DumbService.isDumb(holder.project)) return PsiElementVisitor.EMPTY_VISITOR
        if (holder.file.language != PlayConfigLanguage) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val configFile = file as? PlayConfigFile ?: return
                val props = configFile.getProperties()

                // Group by rawKey (profile + logicalKey combined)
                val seen = mutableMapOf<String, PlayConfigProperty>()
                for (prop in props) {
                    val rawKey = prop.rawKey
                    val existing = seen[rawKey]
                    if (existing != null) {
                        holder.registerProblem(
                            prop.nameIdentifier ?: prop,
                            "Duplicate Play configuration key '${prop.rawKey}' (already defined at line ${getLine(existing)})",
                            ProblemHighlightType.WARNING
                        )
                    } else {
                        seen[rawKey] = prop
                    }
                }
            }
        }
    }

    private fun getLine(prop: PlayConfigProperty): Int {
        val doc = com.intellij.psi.PsiDocumentManager.getInstance(prop.project)
            .getDocument(prop.containingFile) ?: return -1
        return doc.getLineNumber(prop.textOffset) + 1
    }
}

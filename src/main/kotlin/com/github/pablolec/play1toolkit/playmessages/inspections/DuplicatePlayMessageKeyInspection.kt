package com.github.pablolec.play1toolkit.playmessages.inspections

import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesTokenTypes
import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesFile
import com.github.pablolec.play1toolkit.playmessages.psi.PlayMessagesProperty
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFileFactory

class DuplicatePlayMessageKeyInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : com.intellij.psi.PsiElementVisitor() {
            override fun visitFile(file: com.intellij.psi.PsiFile) {
                if (DumbService.isDumb(file.project)) return
                val messagesFile = file as? PlayMessagesFile ?: return
                val seen = mutableMapOf<String, PlayMessagesProperty>()
                for (prop in messagesFile.getProperties()) {
                    val key = prop.key
                    if (key.isBlank()) continue
                    val existing = seen[key]
                    if (existing != null) {
                        val keyElement = prop.node.findChildByType(PlayMessagesTokenTypes.KEY)?.psi ?: prop
                        holder.registerProblem(keyElement, "Duplicate Play message key '$key'")
                    } else {
                        seen[key] = prop
                    }
                }
            }
        }
    }
}

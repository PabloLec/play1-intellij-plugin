package com.github.pablolec.play1toolkit.playconfig.references

import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor

/**
 * Custom rename processor for PlayConfigProperty.
 *
 * When renaming a profiled variant (%docker.db.url → %docker.database.url),
 * offers a choice: rename only this variant, or rename all logical variants + Java usages.
 */
class PlayConfigRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean = element is PlayConfigProperty

    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: com.intellij.psi.search.SearchScope
    ) {
        val prop = element as? PlayConfigProperty ?: return
        val project = prop.project

        if (prop.profile != null) {
            // Ask: rename only this variant, or all logical variants?
            val choice = askUserProfileRenameChoice(project, prop)
            if (choice == RENAME_ALL) {
                addAllLogicalVariants(prop, newName, allRenames, project)
            }
            // else: only this element — allRenames already contains it by default
        } else {
            // Default: rename all logical variants
            addAllLogicalVariants(prop, newName, allRenames, project)
        }
    }

    private fun addAllLogicalVariants(
        prop: PlayConfigProperty,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        project: Project
    ) {
        val svc = PlayConfigService.getInstance(project)
        val allKeys = svc.keysForLogical(prop.logicalKey)
        for (key in allKeys) {
            if (key.property !== prop) {
                allRenames[key.property] = newName
            }
        }
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement {
        return element
    }

    private fun askUserProfileRenameChoice(project: Project, prop: PlayConfigProperty): Int {
        return try {
            Messages.showDialog(
                project,
                "Rename logical key '${prop.logicalKey}' in all profiles (including %${prop.profile}.${prop.logicalKey}), or only this profile variant?",
                "Rename Play Config Key",
                arrayOf("All Logical Variants (recommended)", "This Profile Variant Only"),
                0,
                Messages.getQuestionIcon()
            )
        } catch (e: Exception) {
            RENAME_ALL
        }
    }

    companion object {
        const val RENAME_ALL = 0
        const val RENAME_PROFILE_ONLY = 1
    }
}

package com.github.pablolec.play1toolkit.playjpa.inspection

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

class PlayYamlUnknownFieldInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitKeyValue(keyValue: YAMLKeyValue) {
                if (DumbService.isDumb(keyValue.project)) return
                val file = keyValue.containingFile as? YAMLFile ?: return
                val vf = file.virtualFile ?: return
                if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return
                if (!PlayYamlFixtureUtils.looksLikeFixtureFile(file)) return

                // Only check nested key-values (field names inside a model entry)
                val parentMapping = keyValue.parent as? YAMLMapping ?: return
                val grandparentKv = parentMapping.parent as? YAMLKeyValue ?: return
                val modelName = PlayYamlFixtureUtils.getModelNameFromKey(grandparentKv) ?: return

                val svc = PlayJpaModelService.getInstance(keyValue.project)
                val allFieldNames = svc.getAllFields(modelName).map { it.name }.toSet()
                if (allFieldNames.isEmpty()) return

                val fieldName = keyValue.keyText.trim()
                if (fieldName !in allFieldNames) {
                    val keyElement = keyValue.key ?: return
                    holder.registerProblem(keyElement, "Unknown field '$fieldName' in model '$modelName'")
                }
            }
        }
    }
}

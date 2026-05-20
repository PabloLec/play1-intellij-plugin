package com.github.pablolec.play1toolkit.playjpa.inspection

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

class PlayYamlUnknownModelInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitKeyValue(keyValue: YAMLKeyValue) {
                if (DumbService.isDumb(keyValue.project)) return
                val file = keyValue.containingFile as? YAMLFile ?: return
                val vf = file.virtualFile ?: return
                if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return
                if (!PlayYamlFixtureUtils.looksLikeFixtureFile(file)) return
                val modelName = PlayYamlFixtureUtils.getModelNameFromKey(keyValue) ?: return
                val svc = PlayJpaModelService.getInstance(keyValue.project)
                if (svc.findModelByName(modelName) == null) {
                    val keyElement = keyValue.key ?: return
                    holder.registerProblem(keyElement, "Unknown model '$modelName' in Play fixture")
                }
            }
        }
    }
}

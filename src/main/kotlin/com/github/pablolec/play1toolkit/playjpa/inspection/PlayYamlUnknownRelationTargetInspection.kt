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
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

class PlayYamlUnknownRelationTargetInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Unknown fixture alias in Play fixture relation"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayYamlUnknownRelationTarget"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitKeyValue(keyValue: YAMLKeyValue) {
                if (DumbService.isDumb(keyValue.project)) return
                val file = keyValue.containingFile as? YAMLFile ?: return
                val vf = file.virtualFile ?: return
                if (!PlayYamlFixtureUtils.isFixtureFile(vf) || !PlayYamlFixtureUtils.looksLikeFixtureFile(file)) return

                val parentMapping = keyValue.parent as? YAMLMapping ?: return
                val parentFixture = parentMapping.parent as? YAMLKeyValue ?: return
                val modelName = PlayYamlFixtureUtils.getModelNameFromKey(parentFixture) ?: return

                val svc = PlayJpaModelService.getInstance(keyValue.project)
                val model = svc.findModelByName(modelName) ?: return
                val relation = model.relations.firstOrNull { it.fieldName == keyValue.keyText } ?: return

                val valueScalar = keyValue.value as? YAMLScalar ?: return
                val alias = valueScalar.textValue.trim()
                if (alias.isEmpty() || alias.contains("(") || alias.contains(")")) return

                val targetModel = relation.targetModel ?: return
                val aliases = PlayYamlFixtureUtils.getAllAliasesForModel(file, targetModel)
                if (aliases.isNotEmpty() && alias !in aliases) {
                    holder.registerProblem(valueScalar, "Unknown fixture alias '$alias' for model '$targetModel'")
                }
            }
        }
    }
}

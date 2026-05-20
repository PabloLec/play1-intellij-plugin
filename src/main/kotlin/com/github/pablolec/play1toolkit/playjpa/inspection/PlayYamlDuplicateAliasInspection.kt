package com.github.pablolec.play1toolkit.playjpa.inspection

import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.codeInspection.*
import com.intellij.psi.PsiFile
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.*

class PlayYamlDuplicateAliasInspection : LocalInspectionTool() {
    override fun getDisplayName() = "Duplicate fixture alias in Play fixture"
    override fun getGroupDisplayName() = "Play v1 Toolkit"
    override fun getShortName() = "PlayYamlDuplicateAlias"
    override fun isEnabledByDefault() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val yamlFile = file as? YAMLFile ?: return
                if (DumbService.isDumb(yamlFile.project)) return
                val vf = yamlFile.virtualFile ?: return
                if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return
                if (!PlayYamlFixtureUtils.looksLikeFixtureFile(yamlFile)) return
                val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return
                val seen = mutableSetOf<String>()
                for (kv in topMapping.keyValues) {
                    val keyText = kv.keyText
                    if (PlayYamlFixtureUtils.parseFixtureKey(keyText) == null) continue
                    if (!seen.add(keyText)) {
                        val keyEl = kv.key ?: continue
                        holder.registerProblem(keyEl, "Duplicate fixture alias '$keyText'", ProblemHighlightType.WARNING)
                    }
                }
            }
        }
    }
}

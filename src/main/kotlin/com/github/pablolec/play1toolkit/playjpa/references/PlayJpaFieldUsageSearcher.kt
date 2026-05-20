package com.github.pablolec.play1toolkit.playjpa.references

import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

class PlayJpaFieldUsageSearcher : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

    override fun execute(params: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val field = params.elementToSearch as? PsiField ?: return true
        val project = field.project
        if (DumbService.isDumb(project)) return true

        runReadAction {
            val containingClass = field.containingClass ?: return@runReadAction
            if (!PlayJpaModelUtils.isPlayJpaModel(containingClass)) return@runReadAction
            val className = containingClass.name ?: return@runReadAction
            val fieldName = field.name
            val scope = GlobalSearchScope.projectScope(project)
            val psiManager = PsiManager.getInstance(project)

            for (ext in listOf("yml", "yaml")) {
                FilenameIndex.getAllFilesByExt(project, ext, scope).forEach { vf ->
                    if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return@forEach
                    val yamlFile = psiManager.findFile(vf) as? YAMLFile ?: return@forEach
                    if (!PlayYamlFixtureUtils.looksLikeFixtureFile(yamlFile)) return@forEach
                    val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return@forEach
                    for (modelKv in topMapping.keyValues) {
                        val (modelName, _) = PlayYamlFixtureUtils.parseFixtureKey(modelKv.keyText) ?: continue
                        if (modelName != className) continue
                        val modelMapping = modelKv.value as? YAMLMapping ?: continue
                        for (fieldKv in modelMapping.keyValues) {
                            if (fieldKv.keyText != fieldName) continue
                            val keyEl = fieldKv.key ?: continue
                            keyEl.references.firstOrNull { it.resolve() == field }
                                ?.let { consumer.process(it) }
                        }
                    }
                }
            }
        }
        return true
    }
}

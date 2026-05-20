package com.github.pablolec.play1toolkit.playjpa.references

import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

class PlayJpaModelUsageSearcher : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {

    companion object {
        fun countFixtureUsages(project: com.intellij.openapi.project.Project, psiClass: PsiClass): Int {
            var count = 0
            val className = psiClass.name ?: return 0
            val scope = GlobalSearchScope.projectScope(project)
            val psiManager = PsiManager.getInstance(project)
            for (ext in listOf("yml", "yaml")) {
                FilenameIndex.getAllFilesByExt(project, ext, scope).forEach { vf ->
                    if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return@forEach
                    val yamlFile = psiManager.findFile(vf) as? YAMLFile ?: return@forEach
                    if (!PlayYamlFixtureUtils.looksLikeFixtureFile(yamlFile)) return@forEach
                    val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return@forEach
                    count += topMapping.keyValues.count { PlayYamlFixtureUtils.getModelNameFromKey(it) == className }
                }
            }
            return count
        }
    }

    override fun execute(params: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val psiClass = params.elementToSearch as? PsiClass ?: return true
        val project = psiClass.project
        if (DumbService.isDumb(project)) return true

        runReadAction {
            if (!PlayJpaModelUtils.isPlayJpaModel(psiClass)) return@runReadAction
            val className = psiClass.name ?: return@runReadAction
            val scope = GlobalSearchScope.projectScope(project)
            val psiManager = PsiManager.getInstance(project)

            for (ext in listOf("yml", "yaml")) {
                FilenameIndex.getAllFilesByExt(project, ext, scope).forEach { vf ->
                    if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return@forEach
                    val yamlFile = psiManager.findFile(vf) as? YAMLFile ?: return@forEach
                    if (!PlayYamlFixtureUtils.looksLikeFixtureFile(yamlFile)) return@forEach
                    val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return@forEach
                    for (kv in topMapping.keyValues) {
                        val (modelName, _) = PlayYamlFixtureUtils.parseFixtureKey(kv.keyText) ?: continue
                        if (modelName != className) continue
                        val keyEl = kv.key ?: continue
                        keyEl.references.firstOrNull { it.resolve() == psiClass }
                            ?.let { consumer.process(it) }
                    }
                }
            }
        }
        return true
    }
}

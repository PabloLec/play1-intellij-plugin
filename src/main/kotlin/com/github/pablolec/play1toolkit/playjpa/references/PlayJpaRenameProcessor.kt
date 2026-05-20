package com.github.pablolec.play1toolkit.playjpa.references

import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

class PlayJpaRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean =
        element is PsiClass && PlayJpaModelUtils.isPlayJpaModel(element)

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val psiClass = element as? PsiClass ?: return
        val oldName = psiClass.name ?: return
        val project = psiClass.project
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)

        for (ext in listOf("yml", "yaml")) {
            FilenameIndex.getAllFilesByExt(project, ext, scope).forEach { vf ->
                if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return@forEach
                val yamlFile = psiManager.findFile(vf) as? YAMLFile ?: return@forEach
                if (!PlayYamlFixtureUtils.looksLikeFixtureFile(yamlFile)) return@forEach
                val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return@forEach
                for (kv in topMapping.keyValues) {
                    val (modelName, alias) = PlayYamlFixtureUtils.parseFixtureKey(kv.keyText) ?: continue
                    if (modelName == oldName) {
                        val keyEl = kv.key ?: continue
                        allRenames[keyEl] = "$newName($alias)"
                    }
                }
            }
        }
    }
}

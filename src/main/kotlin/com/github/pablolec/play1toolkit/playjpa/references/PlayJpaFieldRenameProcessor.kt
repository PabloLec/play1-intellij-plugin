package com.github.pablolec.play1toolkit.playjpa.references

import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

class PlayJpaFieldRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        if (element !is PsiField) return false
        val containingClass = element.containingClass ?: return false
        return PlayJpaModelUtils.isPlayJpaModel(containingClass)
    }

    override fun prepareRenaming(
        element: PsiElement,
        newName: String,
        allRenames: MutableMap<PsiElement, String>,
        scope: SearchScope
    ) {
        val field = element as? PsiField ?: return
        val modelClass = field.containingClass ?: return
        val oldName = field.name
        val project = field.project
        val psiManager = PsiManager.getInstance(project)

        val modelName = modelClass.name ?: return
        val projectScope = GlobalSearchScope.projectScope(project)
        for (ext in listOf("yml", "yaml")) {
            FilenameIndex.getAllFilesByExt(project, ext, projectScope).forEach { vf ->
                if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return@forEach
                val yamlFile = psiManager.findFile(vf) as? YAMLFile ?: return@forEach
                if (!PlayYamlFixtureUtils.looksLikeFixtureFile(yamlFile)) return@forEach
                val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return@forEach
                topMapping.keyValues.forEach { fixtureKv ->
                    if (PlayYamlFixtureUtils.getModelNameFromKey(fixtureKv) != modelName) return@forEach
                    val fixtureMapping = fixtureKv.value as? YAMLMapping ?: return@forEach
                    fixtureMapping.keyValues
                        .firstOrNull { it.keyText == oldName }
                        ?.key
                        ?.let { allRenames[it] = newName }
                }
            }
        }
    }
}

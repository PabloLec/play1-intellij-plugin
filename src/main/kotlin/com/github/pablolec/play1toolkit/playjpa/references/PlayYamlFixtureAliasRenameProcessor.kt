package com.github.pablolec.play1toolkit.playjpa.references

import com.github.pablolec.play1toolkit.playjpa.util.PlayYamlFixtureUtils
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

class PlayYamlFixtureAliasRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        val keyValue = element as? YAMLKeyValue ?: return false
        return PlayYamlFixtureUtils.parseFixtureKey(keyValue.keyText) != null
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val keyValue = element as? YAMLKeyValue ?: return
        val (modelName, oldAlias) = PlayYamlFixtureUtils.parseFixtureKey(keyValue.keyText) ?: return
        val keyElement = keyValue.key ?: return
        allRenames[keyElement] = "$modelName($newName)"

        val project = keyValue.project
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        for (ext in listOf("yml", "yaml")) {
            FilenameIndex.getAllFilesByExt(project, ext, scope).forEach { vf ->
                if (!PlayYamlFixtureUtils.isFixtureFile(vf)) return@forEach
                val yamlFile = psiManager.findFile(vf) as? YAMLFile ?: return@forEach
                if (!PlayYamlFixtureUtils.looksLikeFixtureFile(yamlFile)) return@forEach
                val topMapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return@forEach
                topMapping.keyValues.forEach { fixtureKv ->
                    val fixtureModelName = PlayYamlFixtureUtils.getModelNameFromKey(fixtureKv) ?: return@forEach
                    val fixtureMapping = fixtureKv.value as? YAMLMapping ?: return@forEach
                    fixtureMapping.keyValues.forEach { fieldKv ->
                        val scalar = fieldKv.value ?: return@forEach
                        if (scalar.text == oldAlias) {
                            val modelInfo = com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
                                .getInstance(project)
                                .findModelByName(fixtureModelName)
                                ?: return@forEach
                            val relation = modelInfo.relations.firstOrNull { it.fieldName == fieldKv.keyText } ?: return@forEach
                            if (relation.targetModel == modelName) {
                                allRenames[scalar] = newName
                            }
                        }
                    }
                }
            }
        }
    }
}

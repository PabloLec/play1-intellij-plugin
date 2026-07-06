package com.github.pablolec.play1toolkit.playjpa.service

import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaFieldInfo
import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaModelInfo
import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.github.pablolec.play1toolkit.services.Play1ProjectPaths
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class PlayJpaModelService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): PlayJpaModelService =
            project.getService(PlayJpaModelService::class.java)
    }

    fun getAllModels(): List<PlayJpaModelInfo> {
        return ApplicationManager.getApplication().runReadAction<List<PlayJpaModelInfo>> {
            if (DumbService.isDumb(project)) return@runReadAction emptyList()
            val scope = Play1ProjectPaths.indexingScope(project) ?: return@runReadAction emptyList()
            val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)
            javaFiles
                .filter { it.path.contains("/app/models/") }
                .flatMap { vf ->
                    val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return@flatMap emptyList()
                    CachedValuesManager.getCachedValue(psiFile) {
                        val javaFile = psiFile as? PsiJavaFile
                        if (javaFile == null) {
                            CachedValueProvider.Result.create(emptyList<PlayJpaModelInfo>(), PsiModificationTracker.MODIFICATION_COUNT)
                        } else {
                            CachedValueProvider.Result.create(
                                buildModelsFromFile(javaFile),
                                PsiModificationTracker.MODIFICATION_COUNT
                            )
                        }
                    }
                }
        }
    }

    private fun buildModelsFromFile(javaFile: PsiJavaFile): List<PlayJpaModelInfo> {
        return javaFile.classes
            .filter { PlayJpaModelUtils.isPlayJpaModel(it) }
            .map { PlayJpaModelUtils.buildModelInfo(it) }
    }

    fun findModelByName(name: String): PlayJpaModelInfo? =
        getAllModels().firstOrNull { it.className == name }

    fun findModelByQualifiedName(fqn: String): PlayJpaModelInfo? =
        getAllModels().firstOrNull { it.qualifiedName == fqn }

    fun findModelForClass(psiClass: PsiClass): PlayJpaModelInfo? =
        getAllModels().firstOrNull { it.psiClass == psiClass || it.qualifiedName == psiClass.qualifiedName }

    fun getFieldsForModel(className: String): List<PlayJpaFieldInfo> =
        findModelByName(className)?.fields ?: emptyList()

    fun getAllFields(className: String): List<PlayJpaFieldInfo> {
        val model = findModelByName(className) ?: return emptyList()
        return model.fields +
            (model.idField?.let { listOf(it) } ?: emptyList()) +
            model.relations.map { rel ->
                PlayJpaFieldInfo(rel.fieldName, rel.targetModel ?: "Object", rel.psiField, listOf(rel.relationKind.name))
            }
    }

    fun getRelationsForModel(className: String) =
        findModelByName(className)?.relations ?: emptyList()
}

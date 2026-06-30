package com.github.pablolec.play1toolkit.playjpa.service

import com.github.pablolec.play1toolkit.playjpa.model.PlayAppModelEntry
import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class PlayAppModelClassificationService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): PlayAppModelClassificationService =
            project.getService(PlayAppModelClassificationService::class.java)
    }

    fun getAllEntries(): List<PlayAppModelEntry> {
        return ApplicationManager.getApplication().runReadAction<List<PlayAppModelEntry>> {
            if (DumbService.isDumb(project)) return@runReadAction emptyList()
            val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project))
            javaFiles
                .filter { it.path.contains("/app/models/") }
                .flatMap { vf ->
                    val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return@flatMap emptyList()
                    CachedValuesManager.getCachedValue(psiFile) {
                        val javaFile = psiFile as? PsiJavaFile
                        if (javaFile == null) {
                            CachedValueProvider.Result.create(emptyList<PlayAppModelEntry>(), PsiModificationTracker.MODIFICATION_COUNT)
                        } else {
                            CachedValueProvider.Result.create(
                                buildEntriesFromFile(javaFile),
                                PsiModificationTracker.MODIFICATION_COUNT
                            )
                        }
                    }
                }
        }
    }

    private fun buildEntriesFromFile(javaFile: PsiJavaFile): List<PlayAppModelEntry> {
        return javaFile.classes.mapNotNull { psiClass ->
            val classification = PlayJpaModelUtils.classifyAppModel(psiClass) ?: return@mapNotNull null
            PlayAppModelEntry(
                className = psiClass.name ?: return@mapNotNull null,
                qualifiedName = psiClass.qualifiedName,
                psiClass = psiClass,
                classification = classification,
                fieldCount = psiClass.fields.count { !it.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC) },
                methodCount = psiClass.methods.count { !it.isConstructor && !it.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC) },
                enumConstantCount = psiClass.fields.count { it is com.intellij.psi.PsiEnumConstant },
                persistentModel = if (PlayJpaModelUtils.isPlayJpaModel(psiClass)) {
                    PlayJpaModelUtils.buildModelInfo(psiClass)
                } else {
                    null
                }
            )
        }
    }
}

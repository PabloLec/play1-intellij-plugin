package com.github.pablolec.play1toolkit.playjobs.service

import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInfo
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocation
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocationKind
import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

@Service(Service.Level.PROJECT)
class PlayJobService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): PlayJobService =
            project.getService(PlayJobService::class.java)
    }

    fun getAllJobs(): List<PlayJobInfo> {
        if (DumbService.isDumb(project)) return emptyList()
        val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project))
        return javaFiles.flatMap { vf ->
            val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return@flatMap emptyList()
            CachedValuesManager.getCachedValue(psiFile) {
                val javaFile = psiFile as? PsiJavaFile
                if (javaFile == null) {
                    CachedValueProvider.Result.create(emptyList<PlayJobInfo>(), PsiModificationTracker.MODIFICATION_COUNT)
                } else {
                    CachedValueProvider.Result.create(
                        buildJobsFromFile(javaFile),
                        PsiModificationTracker.MODIFICATION_COUNT
                    )
                }
            }
        }
    }

    private fun buildJobsFromFile(javaFile: PsiJavaFile): List<PlayJobInfo> {
        return javaFile.classes.mapNotNull { PlayJobUtils.classify(it) }
    }

    fun findJobByName(name: String): PlayJobInfo? =
        getAllJobs().firstOrNull { it.className == name }

    fun findJobByQualifiedName(fqn: String): PlayJobInfo? =
        getAllJobs().firstOrNull { it.qualifiedName == fqn }

    fun findJobForClass(psiClass: PsiClass): PlayJobInfo? =
        getAllJobs().firstOrNull { it.psiClass == psiClass || it.qualifiedName == psiClass.qualifiedName }

    fun findInvocations(job: PlayJobInfo): List<PlayJobInvocation> = findInvocations(job.psiClass)

    fun findInvocations(jobClass: PsiClass): List<PlayJobInvocation> {
        if (DumbService.isDumb(project)) return emptyList()
        return CachedValuesManager.getCachedValue(jobClass) {
            CachedValueProvider.Result.create(
                computeInvocations(jobClass),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }

    private fun computeInvocations(jobClass: PsiClass): List<PlayJobInvocation> {
        val scope = GlobalSearchScope.projectScope(project)
        return runCatching {
            ReferencesSearch.search(jobClass, scope).findAll()
                .asSequence()
                .mapNotNull { ref ->
                    val newExpr = PsiTreeUtil.getParentOfType(ref.element, PsiNewExpression::class.java)
                        ?: return@mapNotNull null
                    val classRef = newExpr.classReference
                    if (classRef != null && classRef != ref.element) return@mapNotNull null
                    val call = enclosingDirectCall(newExpr)
                    val kind = when (call?.methodExpression?.referenceName) {
                        "now" -> PlayJobInvocationKind.NOW
                        "in" -> PlayJobInvocationKind.IN
                        "at" -> PlayJobInvocationKind.AT
                        "afterRequest" -> PlayJobInvocationKind.AFTER_REQUEST
                        else -> PlayJobInvocationKind.NEW_ONLY
                    }
                    PlayJobInvocation(newExpr, (call ?: newExpr).text, kind)
                }
                .distinctBy { it.psiNewExpression }
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun enclosingDirectCall(newExpr: PsiNewExpression): PsiMethodCallExpression? {
        val parent = newExpr.parent
        val grandParent = parent?.parent
        if (grandParent is PsiMethodCallExpression && grandParent.methodExpression.qualifierExpression === newExpr) {
            return grandParent
        }
        return null
    }
}

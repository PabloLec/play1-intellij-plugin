package com.github.pablolec.play1toolkit.templates.service

import com.github.pablolec.play1toolkit.templates.model.PlayCustomTagInfo
import com.github.pablolec.play1toolkit.templates.model.PlayTemplateFile
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.github.pablolec.play1toolkit.services.Play1ProjectPaths
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

@Service(Service.Level.PROJECT)
class PlayTemplateService(private val project: Project) {

    private val templatesCache = CachedValuesManager.getManager(project).createCachedValue({
        CachedValueProvider.Result.create(
            buildTemplates(),
            PlayTemplateModificationTracker.getInstance(project).modificationTracker()
        )
    }, false)

    private val tagsCache = CachedValuesManager.getManager(project).createCachedValue({
        CachedValueProvider.Result.create(
            buildTags(),
            PlayTemplateModificationTracker.getInstance(project).modificationTracker()
        )
    }, false)

    companion object {
        fun getInstance(project: Project): PlayTemplateService =
            project.getService(PlayTemplateService::class.java)
    }

    fun getAllTemplates(): List<PlayTemplateFile> = cachedTemplates()

    fun getAllCustomTags(): List<PlayCustomTagInfo> = cachedTags()

    fun findTemplate(logicalPath: String): PlayTemplateFile? =
        getAllTemplates().firstOrNull { it.logicalPath == PlayTemplateFileUtils.normalizeTemplatePath(logicalPath) }

    fun findTag(qualifiedName: String): PlayCustomTagInfo? =
        getAllCustomTags().firstOrNull { it.qualifiedName == qualifiedName }

    fun findTagBySimpleName(name: String): PlayCustomTagInfo? =
        getAllCustomTags().firstOrNull { it.name == name }

    fun findLikelyRenderingActions(templateFile: VirtualFile) =
        cachedTemplates()
            .firstOrNull { it.virtualFile == templateFile }
            ?.takeIf { it.controllerName != null && it.actionName != null }

    fun findLikelyRenderingMethods(templateFile: VirtualFile): List<PsiMethod> {
        val template = cachedTemplates().firstOrNull { it.virtualFile == templateFile } ?: return emptyList()
        val controllerName = template.controllerName ?: return emptyList()
        val actionName = template.actionName ?: return emptyList()
        val method = com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
            .resolveMethod(project, controllerName, actionName)
            ?: return emptyList()
        return listOf(method)
    }

    private fun cachedTemplates(): List<PlayTemplateFile> {
        if (DumbService.isDumb(project)) return emptyList()
        return templatesCache.value
    }

    private fun cachedTags(): List<PlayCustomTagInfo> {
        if (DumbService.isDumb(project)) return emptyList()
        return tagsCache.value
    }

    private fun buildTemplates(): List<PlayTemplateFile> {
        val basePath = Play1ProjectPaths.applicationPath(project)
        if (basePath != null) {
            val viewsDir = PlayTemplateFileUtils.resolveTemplatePath(project, "") ?: run {
                val root = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath("$basePath/app/views")
                root ?: return emptyList()
            }
            return collectTemplates(viewsDir)
        }
        return ProjectRootManager.getInstance(project).contentRoots
            .mapNotNull { it.findFileByRelativePath("app/views") }
            .flatMap { collectTemplates(it) }
    }

    private fun buildTags(): List<PlayCustomTagInfo> {
        val basePath = Play1ProjectPaths.applicationPath(project)
        if (basePath != null) {
            val tagsDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath("$basePath/app/views/tags") ?: return emptyList()
            return collectTags(tagsDir)
        }
        return ProjectRootManager.getInstance(project).contentRoots
            .mapNotNull { it.findFileByRelativePath("app/views/tags") }
            .flatMap { collectTags(it) }
    }

    private fun collectTemplates(dir: VirtualFile): List<PlayTemplateFile> {
        val result = mutableListOf<PlayTemplateFile>()
        val basePath = Play1ProjectPaths.applicationPath(project)
        for (child in dir.children) {
            if (child.isDirectory) {
                if (child.name == "tags" && PlayTemplateFileUtils.isInViewsDirectory(child)) {
                    continue
                }
                result.addAll(collectTemplates(child))
            } else if (PlayTemplateFileUtils.isPlayTemplateFile(child)) {
                val logicalPath = basePath?.let { PlayTemplateFileUtils.logicalPath(it, child) }
                    ?: PlayTemplateFileUtils.logicalPath(project, child)
                    ?: continue
                result.add(
                    PlayTemplateFile(
                        logicalPath = logicalPath,
                        virtualFile = child,
                        controllerName = PlayTemplateFileUtils.controllerNameFromLogicalPath(logicalPath),
                        actionName = PlayTemplateFileUtils.actionNameFromLogicalPath(logicalPath)
                    )
                )
            }
        }
        return result
    }

    private fun collectTags(dir: VirtualFile): List<PlayCustomTagInfo> {
        val result = mutableListOf<PlayCustomTagInfo>()
        val basePath = Play1ProjectPaths.applicationPath(project)
        for (child in dir.children) {
            if (child.isDirectory) {
                result.addAll(collectTags(child))
            } else if (PlayTemplateFileUtils.isPlayTemplateFile(child)) {
                val logicalPath = basePath?.let { PlayTemplateFileUtils.logicalPath(it, child) }
                    ?: PlayTemplateFileUtils.logicalPath(project, child)
                    ?: continue
                val qualifiedName = PlayTemplateFileUtils.tagQualifiedName(logicalPath)
                if (qualifiedName.isEmpty()) continue
                val dotIdx = qualifiedName.lastIndexOf('.')
                val name = if (dotIdx < 0) qualifiedName else qualifiedName.substring(dotIdx + 1)
                val params = extractTagParameters(child)
                result.add(
                    PlayCustomTagInfo(
                        name = name,
                        qualifiedName = qualifiedName,
                        logicalPath = logicalPath,
                        virtualFile = child,
                        parameters = params
                    )
                )
            }
        }
        return result
    }

    private fun extractTagParameters(file: VirtualFile): Set<String> {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        val text = psiFile?.text ?: try { String(file.contentsToByteArray()) } catch (_: Exception) { return emptySet() }
        return PlayTemplatePatterns.TAG_PARAM.findAll(text)
            .map { it.groupValues[1] }
            .toSet()
    }
}

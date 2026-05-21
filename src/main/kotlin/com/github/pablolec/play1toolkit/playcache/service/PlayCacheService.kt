package com.github.pablolec.play1toolkit.playcache.service

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsage
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsageKind
import com.github.pablolec.play1toolkit.playcache.model.PlayCachedActionInfo
import com.github.pablolec.play1toolkit.playcache.model.PlayCachedTemplateFragment
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheArgExtractor
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateScanner
import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.response.PlayActionResponseService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

@Service(Service.Level.PROJECT)
class PlayCacheService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): PlayCacheService =
            project.getService(PlayCacheService::class.java)

        private const val CACHE_FOR_FQN = "play.cache.CacheFor"
        private const val CACHE_FOR_SIMPLE = "CacheFor"
    }

    private data class JavaFilePass(
        val usages: List<PlayCacheUsage>,
        val cachedActions: List<PlayCachedActionInfo>
    )

    fun getAllUsages(): List<PlayCacheUsage> {
        if (DumbService.isDumb(project)) return emptyList()
        return javaUsages() + templateUsages()
    }

    fun getUsagesByStaticKey(key: String): List<PlayCacheUsage> =
        getAllUsages().filter { (it.key as? PlayCacheKey.Static)?.value == key }

    fun getKnownStaticKeys(): Set<String> {
        if (DumbService.isDumb(project)) return emptySet()
        return getAllUsages().mapNotNullTo(sortedSetOf()) { (it.key as? PlayCacheKey.Static)?.value }
    }

    fun getTemplateFragments(): List<PlayCachedTemplateFragment> {
        if (DumbService.isDumb(project)) return emptyList()
        return templateFiles().flatMap { vf ->
            val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return@flatMap emptyList()
            if (!PlayCacheTemplateScanner.isEligible(psiFile)) return@flatMap emptyList()
            CachedValuesManager.getCachedValue(psiFile) {
                CachedValueProvider.Result.create(
                    PlayCacheTemplateScanner.scan(psiFile),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        }
    }

    fun getCachedActions(): List<PlayCachedActionInfo> {
        if (DumbService.isDumb(project)) return emptyList()
        return javaFilePasses().flatMap { it.cachedActions }
    }

    fun getDynamicUsages(): List<PlayCacheUsage> =
        getAllUsages().filter { it.key is PlayCacheKey.Dynamic || it.key is PlayCacheKey.Pattern }

    fun getGlobalClears(): List<PlayCacheUsage> =
        getAllUsages().filter { it.kind == PlayCacheUsageKind.JAVA_CLEAR }

    fun findCachedAction(method: PsiMethod): PlayCachedActionInfo? =
        getCachedActions().firstOrNull { it.actionMethod == method }

    private fun javaUsages(): List<PlayCacheUsage> =
        javaFilePasses().flatMap { it.usages }

    private fun javaFilePasses(): List<JavaFilePass> {
        val javaFiles = FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project))
        return javaFiles.mapNotNull { vf ->
            val psiFile = PsiManager.getInstance(project).findFile(vf) as? PsiJavaFile ?: return@mapNotNull null
            CachedValuesManager.getCachedValue(psiFile) {
                CachedValueProvider.Result.create(
                    scanJavaFile(psiFile),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        }
    }

    private fun templateUsages(): List<PlayCacheUsage> {
        val fragments = getTemplateFragments()
        return fragments.map { fragment ->
            val anchor = fragment.templateFile.findElementAt(fragment.openTagRange.startOffset) ?: fragment.templateFile
            PlayCacheUsage(
                kind = PlayCacheUsageKind.TEMPLATE_FRAGMENT,
                key = fragment.key,
                ttl = fragment.ttl,
                sourceElement = anchor,
                ownerDescription = fragment.templateFile.virtualFile?.path
                    ?.substringAfterLast("/app/views/")
                    ?: fragment.templateFile.name,
                containingFile = fragment.templateFile.virtualFile,
                details = fragment.includedTemplatePaths
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") { "include $it" }
            )
        }
    }

    private fun templateFiles(): List<com.intellij.openapi.vfs.VirtualFile> {
        val scope = GlobalSearchScope.projectScope(project)
        val out = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
        listOf("html", "xml", "json", "txt").forEach { ext ->
            out += FilenameIndex.getAllFilesByExt(project, ext, scope)
        }
        return out
            .filter { it.path.contains("/app/views/") }
            .distinctBy { it.path }
    }

    private fun scanJavaFile(file: PsiJavaFile): JavaFilePass {
        val usages = mutableListOf<PlayCacheUsage>()
        val cachedActions = mutableListOf<PlayCachedActionInfo>()

        for (psiClass in file.classes) {
            for (method in psiClass.methods) {
                collectCachedAction(psiClass, method)?.let { cachedActions += it }
            }
            collectCacheCalls(psiClass, usages)
        }
        return JavaFilePass(usages, cachedActions)
    }

    private fun collectCachedAction(psiClass: PsiClass, method: PsiMethod): PlayCachedActionInfo? {
        val modifierList = method.modifierList
        val annotation = modifierList.annotations.firstOrNull { annotationMatchesCacheFor(it) } ?: return null
        val value = (annotation.findAttributeValue("value") as? PsiLiteralExpression)?.value as? String
        val ttl = when {
            value == null -> PlayCacheTtl.Absent
            else -> PlayCacheTtl.Static(value)
        }
        val routes = Play1ViewUtils.findRoutesForAction(project, psiClass.name ?: return null, method.name)
        val responseInfo = runCatching { PlayActionResponseService.getInstance(project).analyze(method) }.getOrNull()
        return PlayCachedActionInfo(
            controllerClass = psiClass,
            actionMethod = method,
            ttl = ttl,
            annotation = annotation,
            routes = routes,
            responseInfo = responseInfo
        )
    }

    private fun annotationMatchesCacheFor(annotation: PsiAnnotation): Boolean {
        val qn = annotation.qualifiedName
        if (qn == CACHE_FOR_FQN) return true
        val simple = annotation.nameReferenceElement?.referenceName
        return simple == CACHE_FOR_SIMPLE
    }

    private fun collectCacheCalls(psiClass: PsiClass, out: MutableList<PlayCacheUsage>) {
        val containingFile = psiClass.containingFile?.virtualFile
        PsiTreeUtil.findChildrenOfType(psiClass, PsiMethodCallExpression::class.java).forEach { call ->
            if (!PlayCacheArgExtractor.isCacheCall(call)) return@forEach
            val kind = PlayCacheArgExtractor.methodKind(call) ?: return@forEach
            val arguments = call.argumentList.expressions
            val key: PlayCacheKey
            val ttl: PlayCacheTtl
            val valueText: String?
            val keyConfigKey: String?
            val ttlConfigKey: String?
            val valueType: String?
            when (kind) {
                PlayCacheUsageKind.JAVA_CLEAR -> {
                    key = PlayCacheKey.Missing
                    ttl = PlayCacheTtl.Absent
                    valueText = null
                    keyConfigKey = null
                    ttlConfigKey = null
                    valueType = null
                }
                PlayCacheUsageKind.JAVA_READ,
                PlayCacheUsageKind.JAVA_INVALIDATION,
                PlayCacheUsageKind.JAVA_MUTATION -> {
                    key = PlayCacheArgExtractor.extractKey(arguments.firstOrNull())
                    ttl = PlayCacheTtl.Absent
                    valueText = null
                    keyConfigKey = PlayCacheArgExtractor.extractConfigKey(arguments.firstOrNull())
                    ttlConfigKey = null
                    valueType = null
                }
                PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> {
                    key = PlayCacheArgExtractor.extractKey(arguments.firstOrNull())
                    val ttlArg = arguments.getOrNull(2)
                    ttl = ttlArg?.let { PlayCacheArgExtractor.extractTtl(it) } ?: PlayCacheTtl.Absent
                    val valueExpr = arguments.getOrNull(1)
                    valueText = valueExpr?.text
                    keyConfigKey = PlayCacheArgExtractor.extractConfigKey(arguments.firstOrNull())
                    ttlConfigKey = PlayCacheArgExtractor.extractConfigKey(ttlArg)
                    valueType = valueExpr?.type?.presentableText
                }
                PlayCacheUsageKind.JAVA_WRITE,
                PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT,
                PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT -> {
                    key = PlayCacheArgExtractor.extractKey(arguments.firstOrNull())
                    val ttlArg: PsiExpression? = arguments.getOrNull(2)
                    ttl = PlayCacheArgExtractor.extractTtl(ttlArg)
                    val valueExpr = arguments.getOrNull(1)
                    valueText = valueExpr?.text
                    keyConfigKey = PlayCacheArgExtractor.extractConfigKey(arguments.firstOrNull())
                    ttlConfigKey = PlayCacheArgExtractor.extractConfigKey(ttlArg)
                    valueType = valueExpr?.type?.presentableText
                }
                else -> {
                    key = PlayCacheKey.Missing
                    ttl = PlayCacheTtl.Absent
                    valueText = null
                    keyConfigKey = null
                    ttlConfigKey = null
                    valueType = null
                }
            }
            out += PlayCacheUsage(
                kind = kind,
                key = key,
                ttl = ttl,
                sourceElement = call,
                ownerDescription = describeOwner(call, psiClass),
                containingFile = containingFile,
                details = valueText,
                keyConfigurationKey = keyConfigKey,
                ttlConfigurationKey = ttlConfigKey,
                valueType = valueType
            )
        }
    }

    private fun describeOwner(call: PsiMethodCallExpression, fallbackClass: PsiClass): String {
        val method = PsiTreeUtil.getParentOfType(call, PsiMethod::class.java)
        val containingClass = method?.containingClass ?: fallbackClass
        val className = containingClass.name ?: "?"
        val methodName = method?.name ?: "<init>"
        return "$className.$methodName"
    }
}

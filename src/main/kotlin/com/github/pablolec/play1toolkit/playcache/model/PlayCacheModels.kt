package com.github.pablolec.play1toolkit.playcache.model

import com.github.pablolec.play1toolkit.response.PlayEndpointResponseInfo
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod

enum class PlayCacheUsageKind {
    TEMPLATE_FRAGMENT,
    CACHED_ACTION,
    JAVA_READ,
    JAVA_READ_OR_COMPUTE,
    JAVA_WRITE,
    JAVA_WRITE_IF_ABSENT,
    JAVA_WRITE_IF_PRESENT,
    JAVA_INVALIDATION,
    JAVA_CLEAR,
    JAVA_MUTATION
}

sealed interface PlayCacheKey {
    data class Static(val value: String) : PlayCacheKey
    data class Pattern(val value: String) : PlayCacheKey
    data class Dynamic(val expressionText: String) : PlayCacheKey
    data object Missing : PlayCacheKey
}

sealed interface PlayCacheTtl {
    data class Static(val value: String) : PlayCacheTtl
    data class Dynamic(val expressionText: String) : PlayCacheTtl
    data object Absent : PlayCacheTtl
}

data class PlayCacheUsage(
    val kind: PlayCacheUsageKind,
    val key: PlayCacheKey,
    val ttl: PlayCacheTtl,
    val sourceElement: PsiElement,
    val ownerDescription: String,
    val containingFile: VirtualFile?,
    val details: String? = null,
    val keyConfigurationKey: String? = null,
    val ttlConfigurationKey: String? = null,
    val valueType: String? = null
)

data class PlayCachedActionInfo(
    val controllerClass: PsiClass,
    val actionMethod: PsiMethod,
    val ttl: PlayCacheTtl,
    val annotation: PsiAnnotation,
    val routes: List<RoutesRouteElement>,
    val responseInfo: PlayEndpointResponseInfo?
)

data class PlayCachedTemplateFragment(
    val templateFile: PsiFile,
    val key: PlayCacheKey,
    val ttl: PlayCacheTtl,
    val rawKeyText: String,
    val rawTtlText: String?,
    val openTagRange: TextRange,
    val bodyRange: TextRange,
    val includedTemplatePaths: List<String>
)

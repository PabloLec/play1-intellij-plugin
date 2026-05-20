package com.github.pablolec.play1toolkit.response

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

class PlayActionResponseService(private val project: Project) {
    private val analyzer = PlayActionResponseAnalyzer(project)

    fun analyze(method: PsiMethod): PlayEndpointResponseInfo =
        CachedValuesManager.getCachedValue(method) {
            CachedValueProvider.Result.create(
                analyzer.analyze(method),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }

    fun isPlayActionMethod(method: PsiMethod): Boolean = analyzer.isPlayActionMethod(method)

    companion object {
        fun getInstance(project: Project): PlayActionResponseService = project.getService(PlayActionResponseService::class.java)
    }
}

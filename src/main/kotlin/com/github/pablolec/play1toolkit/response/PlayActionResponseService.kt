package com.github.pablolec.play1toolkit.response

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

class PlayActionResponseService(private val project: Project) {
    private val analyzer = PlayActionResponseAnalyzer(project)

    fun analyze(method: PsiMethod): PlayEndpointResponseInfo =
        ApplicationManager.getApplication().runReadAction<PlayEndpointResponseInfo> {
            CachedValuesManager.getCachedValue(method) {
                CachedValueProvider.Result.create(
                    analyzer.analyze(method),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        }

    fun isPlayActionMethod(method: PsiMethod): Boolean =
        ApplicationManager.getApplication().runReadAction<Boolean> {
            analyzer.isPlayActionMethod(method)
        }

    companion object {
        fun getInstance(project: Project): PlayActionResponseService = project.getService(PlayActionResponseService::class.java)
    }
}

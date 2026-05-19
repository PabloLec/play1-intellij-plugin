package com.github.pablolec.play1toolkit.routes

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

/**
 * Resolves Play 1 controller names and action methods to PSI elements.
 *
 * Play 1 conventions:
 *   - "Application" lives at controllers.Application
 *   - "login.LoginCtl" lives at controllers.login.LoginCtl
 *
 * Resolution order (project scope first to avoid false matches from libraries):
 *   1. Exact FQN in project scope
 *   2. "controllers.{name}" in project scope
 *   3. Short class name (last component) via PsiShortNamesCache in project scope
 *   4. Repeat 1–3 with allScope (handles unconfigured source roots)
 */
object RoutesControllerResolver {

    fun resolveClass(project: Project, controllerName: String): PsiClass? {
        if (controllerName.isEmpty() || controllerName.contains('{')) return null
        val psiFacade = JavaPsiFacade.getInstance(project)
        val shortName = controllerName.substringAfterLast('.')

        for (scope in scopes(project)) {
            psiFacade.findClass(controllerName, scope)?.let { return it }
            psiFacade.findClass("controllers.$controllerName", scope)?.let { return it }
            PsiShortNamesCache.getInstance(project)
                .getClassesByName(shortName, scope).firstOrNull()?.let { return it }
        }
        return null
    }

    fun resolveMethod(project: Project, controllerName: String, actionName: String): PsiMethod? {
        if (actionName.isEmpty()) return null
        val psiClass = resolveClass(project, controllerName) ?: return null
        return psiClass.findMethodsByName(actionName, true).firstOrNull()
    }

    private fun scopes(project: Project) = listOf(
        GlobalSearchScope.projectScope(project),
        GlobalSearchScope.allScope(project),
    )
}

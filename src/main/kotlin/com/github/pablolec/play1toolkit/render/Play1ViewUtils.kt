package com.github.pablolec.play1toolkit.render

import com.github.pablolec.play1toolkit.services.Play1ProjectPaths
import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.InheritanceUtil

object Play1ViewUtils {

    fun isPlayController(psiClass: PsiClass): Boolean =
        ApplicationManager.getApplication().runReadAction<Boolean> {
            InheritanceUtil.isInheritor(psiClass, "play.mvc.Controller")
        }

    fun implicitViewPath(controllerName: String, actionName: String): String =
        "app/views/$controllerName/$actionName.html"

    fun findViewFile(project: Project, controllerName: String, actionName: String): VirtualFile? {
        val root = Play1ProjectPaths.applicationRoot(project) ?: return null
        return root.findFileByRelativePath("app/views/$controllerName/$actionName.html")
            ?: root.findFileByRelativePath("app/views/$controllerName/$actionName.groovy")
    }

    fun findRoutesFile(project: Project): VirtualFile? {
        return Play1ProjectPaths.applicationRoot(project)?.findFileByRelativePath("conf/routes")
    }

    /**
     * Returns routes whose controller short name matches [controllerShortName] and action equals [actionName].
     * After the lexer fix, route controller text may be "login.LoginCtl" — we match on the last component.
     */
    fun findRoutesForAction(
        project: Project,
        controllerShortName: String,
        actionName: String
    ): List<RoutesRouteElement> {
        return ApplicationManager.getApplication().runReadAction<List<RoutesRouteElement>> {
            val routesVf = findRoutesFile(project) ?: return@runReadAction emptyList()
            val psiFile = PsiManager.getInstance(project).findFile(routesVf) as? RoutesFile ?: return@runReadAction emptyList()
            psiFile.getRoutes().filter { route ->
                route.isDynamicRoute() &&
                    route.getControllerName()?.text?.trim()?.substringAfterLast('.') == controllerShortName &&
                    route.getActionName()?.text?.trim() == actionName
            }
        }
    }

    /** Returns all routes whose controller short name matches [controllerShortName]. */
    fun findAllRoutesForController(
        project: Project,
        controllerShortName: String
    ): List<RoutesRouteElement> {
        return ApplicationManager.getApplication().runReadAction<List<RoutesRouteElement>> {
            val routesVf = findRoutesFile(project) ?: return@runReadAction emptyList()
            val psiFile = PsiManager.getInstance(project).findFile(routesVf) as? RoutesFile ?: return@runReadAction emptyList()
            psiFile.getRoutes().filter { route ->
                route.isDynamicRoute() &&
                    route.getControllerName()?.text?.trim()?.substringAfterLast('.') == controllerShortName
            }
        }
    }

    /** True if [psiClass] is a Play 1 controller — checks both inheritance and package as fallback. */
    fun isPlayControllerClass(psiClass: PsiClass): Boolean =
        ApplicationManager.getApplication().runReadAction<Boolean> {
            isPlayController(psiClass) || psiClass.qualifiedName?.startsWith("controllers") == true
        }
}

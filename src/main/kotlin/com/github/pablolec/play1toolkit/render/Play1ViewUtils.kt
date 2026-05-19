package com.github.pablolec.play1toolkit.render

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.util.InheritanceUtil
import java.nio.file.Paths

object Play1ViewUtils {

    fun isPlayController(psiClass: PsiClass): Boolean =
        InheritanceUtil.isInheritor(psiClass, "play.mvc.Controller")

    fun implicitViewPath(controllerName: String, actionName: String): String =
        "app/views/$controllerName/$actionName.html"

    fun findViewFile(project: Project, controllerName: String, actionName: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val vfm = VirtualFileManager.getInstance()
        return vfm.findFileByNioPath(Paths.get(basePath, "app", "views", controllerName, "$actionName.html"))
            ?: vfm.findFileByNioPath(Paths.get(basePath, "app", "views", controllerName, "$actionName.groovy"))
    }

    fun findRoutesFile(project: Project): VirtualFile? {
        val basePath = project.basePath ?: return null
        return VirtualFileManager.getInstance().findFileByNioPath(Paths.get(basePath, "conf", "routes"))
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
        val routesVf = findRoutesFile(project) ?: return emptyList()
        val psiFile = PsiManager.getInstance(project).findFile(routesVf) as? RoutesFile ?: return emptyList()
        return psiFile.getRoutes().filter { route ->
            route.isDynamicRoute() &&
                route.getControllerName()?.text?.trim()?.substringAfterLast('.') == controllerShortName &&
                route.getActionName()?.text?.trim() == actionName
        }
    }

    /** Returns all routes whose controller short name matches [controllerShortName]. */
    fun findAllRoutesForController(
        project: Project,
        controllerShortName: String
    ): List<RoutesRouteElement> {
        val routesVf = findRoutesFile(project) ?: return emptyList()
        val psiFile = PsiManager.getInstance(project).findFile(routesVf) as? RoutesFile ?: return emptyList()
        return psiFile.getRoutes().filter { route ->
            route.isDynamicRoute() &&
                route.getControllerName()?.text?.trim()?.substringAfterLast('.') == controllerShortName
        }
    }

    /** True if [psiClass] is a Play 1 controller — checks both inheritance and package as fallback. */
    fun isPlayControllerClass(psiClass: PsiClass): Boolean =
        isPlayController(psiClass) || psiClass.qualifiedName?.startsWith("controllers") == true
}

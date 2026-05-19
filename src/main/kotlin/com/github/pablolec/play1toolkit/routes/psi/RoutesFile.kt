package com.github.pablolec.play1toolkit.routes.psi

import com.github.pablolec.play1toolkit.routes.RoutesFileType
import com.github.pablolec.play1toolkit.routes.RoutesLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class RoutesFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RoutesLanguage) {
    override fun getFileType(): FileType = RoutesFileType
    override fun toString(): String = "Routes File"

    fun getRoutes(): List<RoutesRouteElement> = children.filterIsInstance<RoutesRouteElement>()
}

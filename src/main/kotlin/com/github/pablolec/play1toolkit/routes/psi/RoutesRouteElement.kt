package com.github.pablolec.play1toolkit.routes.psi

import com.github.pablolec.play1toolkit.routes.RoutesTokenTypes
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

class RoutesRouteElement(node: ASTNode) : ASTWrapperPsiElement(node) {

    fun getHttpMethod(): PsiElement? = findChildByType(RoutesTokenTypes.HTTP_METHOD)
    fun getControllerName(): PsiElement? = findChildByType(RoutesTokenTypes.CONTROLLER_NAME)
    fun getDot(): PsiElement? = findChildByType(RoutesTokenTypes.DOT)
    fun getActionName(): PsiElement? = findChildByType(RoutesTokenTypes.ACTION_NAME)
    fun getStaticRef(): PsiElement? = findChildByType(RoutesTokenTypes.STATIC_REF)
    fun getModuleRef(): PsiElement? = findChildByType(RoutesTokenTypes.MODULE_REF)

    fun isStaticRoute(): Boolean = getStaticRef() != null
    fun isModuleRoute(): Boolean = getModuleRef() != null
    fun isDynamicRoute(): Boolean = !isStaticRoute() && !isModuleRoute()
}

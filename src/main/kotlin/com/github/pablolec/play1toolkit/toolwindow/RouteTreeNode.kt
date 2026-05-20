package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.response.PlayEndpointResponseInfo
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement

sealed class RouteTreeNode {
    data class ControllerNode(val name: String) : RouteTreeNode()
    data class PathNode(val segment: String) : RouteTreeNode()
    data class RouteEntry(
        val method: String,
        val path: String,
        val controllerName: String,
        val actionName: String,
        val responseInfo: PlayEndpointResponseInfo,
        val psiElement: RoutesRouteElement,
    ) : RouteTreeNode()
    data class SpecialEntry(val label: String) : RouteTreeNode()
}

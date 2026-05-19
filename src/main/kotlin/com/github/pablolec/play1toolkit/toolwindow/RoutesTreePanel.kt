package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class RoutesTreePanel(private val project: Project) : JBPanel<RoutesTreePanel>(BorderLayout()) {

    private val routeCountLabel = JBLabel("Routes: —")
    private val tree = JTree(DefaultMutableTreeNode("Routes"))

    init {
        border = JBUI.Borders.emptyTop(4)
        tree.isRootVisible = false

        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 8)
            add(routeCountLabel, BorderLayout.WEST)
        }

        add(header, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
        refresh()
    }

    fun refresh() {
        val routesFile = findRoutesFile() ?: run {
            routeCountLabel.text = "Routes: conf/routes not found"
            tree.model = DefaultTreeModel(DefaultMutableTreeNode("No routes file"))
            return
        }

        val routes = routesFile.getRoutes()
        routeCountLabel.text = "Routes: ${routes.size}"

        val root = DefaultMutableTreeNode("Routes")
        routes.forEach { route -> root.add(routeNodeFor(route)) }
        tree.model = DefaultTreeModel(root)

        expandAll()
        revalidate()
        repaint()
    }

    private fun findRoutesFile(): RoutesFile? {
        val basePath = project.basePath ?: return null
        val routesPath = Paths.get(basePath, "conf", "routes").toString()
        val vFile = VirtualFileManager.getInstance()
            .findFileByNioPath(Paths.get(routesPath)) ?: return null
        return PsiManager.getInstance(project).findFile(vFile) as? RoutesFile
    }

    private fun routeNodeFor(route: RoutesRouteElement): DefaultMutableTreeNode {
        val method = route.getHttpMethod()?.text ?: "?"
        val label = when {
            route.isStaticRoute() -> {
                val ref = route.getStaticRef()?.text ?: "staticDir"
                "$method  →  $ref"
            }
            route.isModuleRoute() -> {
                val ref = route.getModuleRef()?.text ?: "module"
                "$method  →  $ref"
            }
            else -> {
                val ctrl = route.getControllerName()?.text ?: "?"
                val action = route.getActionName()?.text ?: "?"
                "$method  →  $ctrl.$action"
            }
        }
        return DefaultMutableTreeNode(label)
    }

    private fun expandAll() {
        var i = 0
        while (i < tree.rowCount) {
            tree.expandRow(i++)
        }
    }
}

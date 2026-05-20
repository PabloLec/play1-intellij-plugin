package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.pom.Navigatable
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

enum class RoutesViewMode { BY_CONTROLLER, BY_PATH }

class RoutesTreePanel(private val project: Project) : JBPanel<RoutesTreePanel>(BorderLayout()) {

    private val routeCountLabel = JBLabel("Routes: —")
    private val tree = JTree(DefaultMutableTreeNode("Routes"))
    private var viewMode = RoutesViewMode.BY_CONTROLLER

    init {
        border = JBUI.Borders.emptyTop(4)
        tree.isRootVisible = false
        tree.cellRenderer = RouteTreeCellRenderer()

        val byCtrlButton = JButton("By Controller").apply {
            isOpaque = false
            addActionListener { viewMode = RoutesViewMode.BY_CONTROLLER; refresh() }
        }
        val byPathButton = JButton("By Path").apply {
            isOpaque = false
            addActionListener { viewMode = RoutesViewMode.BY_PATH; refresh() }
        }

        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(routeCountLabel, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                isOpaque = false
                add(byCtrlButton)
                add(byPathButton)
            }, BorderLayout.EAST)
            isOpaque = false
        }

        add(header, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val entry = node.userObject as? RouteTreeNode.RouteEntry ?: return
                if (e.clickCount >= 2) navigateToRoutesLine(entry) else navigateToJavaMethod(entry)
            }
        })

        refresh()
    }

    fun refresh() {
        routeCountLabel.text = "Routes: loading…"
        ReadAction.nonBlocking<Pair<Int, DefaultMutableTreeNode>> {
            val routesFile = findRoutesFile()
                ?: return@nonBlocking Pair(-1, DefaultMutableTreeNode("conf/routes not found"))
            val routes = routesFile.getRoutes()
            val root = when (viewMode) {
                RoutesViewMode.BY_CONTROLLER -> buildControllerTree(routes)
                RoutesViewMode.BY_PATH -> buildPathTree(routes)
            }
            Pair(routes.size, root)
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { (count, root) ->
                routeCountLabel.text = if (count < 0) "Routes: conf/routes not found" else "Routes: $count"
                tree.model = DefaultTreeModel(root)
                revalidate()
                repaint()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun findRoutesFile(): RoutesFile? {
        val basePath = project.basePath ?: return null
        val vFile = VirtualFileManager.getInstance()
            .findFileByNioPath(Paths.get(basePath, "conf", "routes")) ?: return null
        return PsiManager.getInstance(project).findFile(vFile) as? RoutesFile
    }

    // --- Tree builders ---

    private fun buildControllerTree(routes: List<RoutesRouteElement>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Routes")

        routes.filter { it.isDynamicRoute() }
            .groupBy { it.getControllerName()?.text?.trim() ?: "?" }
            .toSortedMap()
            .forEach { (ctrl, entries) ->
                val ctrlNode = DefaultMutableTreeNode(RouteTreeNode.ControllerNode(ctrl))
                entries.forEach { ctrlNode.add(DefaultMutableTreeNode(routeEntryFor(it))) }
                root.add(ctrlNode)
            }

        routes.filter { !it.isDynamicRoute() }.forEach { route ->
            root.add(DefaultMutableTreeNode(specialEntryFor(route)))
        }
        return root
    }

    private fun buildPathTree(routes: List<RoutesRouteElement>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Routes")

        routes.filter { !it.isDynamicRoute() }.forEach { route ->
            root.add(DefaultMutableTreeNode(specialEntryFor(route)))
        }

        routes.filter { it.isDynamicRoute() }.forEach { route ->
            val rawPath = route.getPath()?.trim() ?: "/"
            val segments = rawPath.split("/").filter { it.isNotEmpty() }
            insertPathEntry(root, segments, routeEntryFor(route))
        }
        return root
    }

    private fun insertPathEntry(
        parent: DefaultMutableTreeNode,
        segments: List<String>,
        entry: RouteTreeNode.RouteEntry,
    ) {
        if (segments.isEmpty()) {
            parent.add(DefaultMutableTreeNode(entry))
            return
        }
        val seg = segments.first()
        val existing = (0 until parent.childCount)
            .map { parent.getChildAt(it) as DefaultMutableTreeNode }
            .firstOrNull { (it.userObject as? RouteTreeNode.PathNode)?.segment == seg }
        val pathNode = existing ?: DefaultMutableTreeNode(RouteTreeNode.PathNode(seg)).also { parent.add(it) }
        insertPathEntry(pathNode, segments.drop(1), entry)
    }

    private fun routeEntryFor(route: RoutesRouteElement) = RouteTreeNode.RouteEntry(
        method = route.getHttpMethod()?.text?.trim() ?: "?",
        path = route.getPath()?.trim() ?: "",
        controllerName = route.getControllerName()?.text?.trim() ?: "?",
        actionName = route.getActionName()?.text?.trim() ?: "?",
        psiElement = route,
    )

    private fun specialEntryFor(route: RoutesRouteElement): RouteTreeNode.SpecialEntry {
        val label = when {
            route.isStaticRoute() -> "staticDir:${route.getStaticRef()?.text ?: ""}"
            route.isModuleRoute() -> "module:${route.getModuleRef()?.text ?: ""}"
            else -> "?"
        }
        return RouteTreeNode.SpecialEntry(label)
    }

    // --- Navigation ---

    private fun navigateToJavaMethod(entry: RouteTreeNode.RouteEntry) {
        ReadAction.nonBlocking<Navigatable?> {
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)
            val psiClass = facade.findClass(entry.controllerName, scope)
                ?: PsiShortNamesCache.getInstance(project)
                    .getClassesByName(entry.controllerName, scope).firstOrNull()
                ?: return@nonBlocking null
            (psiClass.findMethodsByName(entry.actionName, true).firstOrNull() ?: psiClass) as Navigatable
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { nav ->
                nav?.navigate(true)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun navigateToRoutesLine(entry: RouteTreeNode.RouteEntry) {
        val basePath = project.basePath ?: return
        val vFile = VirtualFileManager.getInstance()
            .findFileByNioPath(Paths.get(basePath, "conf", "routes")) ?: return
        ReadAction.nonBlocking<Int> {
            val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@nonBlocking 0
            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return@nonBlocking 0
            doc.getLineNumber(entry.psiElement.textOffset)
        }
            .finishOnUiThread(ModalityState.defaultModalityState()) { line ->
                OpenFileDescriptor(project, vFile, line, 0).navigate(true)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

}

private class RouteTreeCellRenderer : DefaultTreeCellRenderer() {

    private val methodColors = mapOf(
        "GET" to Color(0x4090D0),
        "POST" to Color(0x50A050),
        "PUT" to Color(0xD0900A),
        "DELETE" to Color(0xC03030),
        "PATCH" to Color(0x9050C0),
        "*" to Color(0x808080),
    )

    override fun getTreeCellRendererComponent(
        tree: JTree, value: Any, selected: Boolean, expanded: Boolean,
        leaf: Boolean, row: Int, hasFocus: Boolean,
    ): Component {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val node = (value as? DefaultMutableTreeNode)?.userObject
        icon = null
        when (node) {
            is RouteTreeNode.ControllerNode -> {
                text = node.name
                icon = com.intellij.icons.AllIcons.Nodes.Class
            }
            is RouteTreeNode.PathNode -> {
                text = "/${node.segment}"
                icon = com.intellij.icons.AllIcons.Nodes.Package
            }
            is RouteTreeNode.RouteEntry -> {
                val color = methodColors[node.method] ?: Color(0x606060)
                val hex = String.format("%06X", color.rgb and 0xFFFFFF)
                text = "<html><b><font color='#$hex'>${node.method}</font></b>&nbsp;&nbsp;" +
                    "${esc(node.path)}&nbsp;&nbsp;<font color='gray'>→ ${esc(node.actionName)}</font></html>"
            }
            is RouteTreeNode.SpecialEntry -> {
                text = "<html><font color='gray'><i>${esc(node.label)}</i></font></html>"
            }
            else -> { /* root node — hidden */ }
        }
        return this
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

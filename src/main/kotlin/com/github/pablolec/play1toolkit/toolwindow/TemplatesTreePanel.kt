package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.templates.model.PlayCustomTagInfo
import com.github.pablolec.play1toolkit.templates.model.PlayTemplateFile
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.JTree
import javax.swing.ToolTipManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class TemplatesTreePanel(private val project: Project) : JBPanel<TemplatesTreePanel>(BorderLayout()) {

    private val summaryLabel = JBLabel("Templates: —")
    private val tree = JTree(DefaultMutableTreeNode("Templates"))

    init {
        border = JBUI.Borders.emptyTop(4)
        tree.isRootVisible = false
        tree.cellRenderer = TemplateTreeCellRenderer()
        ToolTipManager.sharedInstance().registerComponent(tree)

        add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(summaryLabel, BorderLayout.WEST)
            isOpaque = false
        }, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                when (val userObject = node.userObject) {
                    is TemplateNode.TemplateLeaf -> {
                        if (e.clickCount >= 2) {
                            navigateToLikelyAction(userObject.template)
                        } else {
                            OpenFileDescriptor(project, userObject.template.virtualFile).navigate(true)
                        }
                    }
                    is TemplateNode.TagLeaf -> OpenFileDescriptor(project, userObject.tag.virtualFile).navigate(true)
                }
            }
        })

        refresh()
    }

    fun refresh() {
        summaryLabel.text = "Templates: loading…"
        ReadAction.nonBlocking<Triple<Int, Int, DefaultMutableTreeNode>> {
            val service = PlayTemplateService.getInstance(project)
            val templates = service.getAllTemplates().sortedBy { it.logicalPath.lowercase(Locale.ROOT) }
            val tags = service.getAllCustomTags().sortedBy { it.qualifiedName.lowercase(Locale.ROOT) }
            Triple(templates.size, tags.size, buildTree(templates, tags))
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { (templateCount, tagCount, root) ->
                summaryLabel.text = "Templates: $templateCount · Tags: $tagCount"
                tree.model = DefaultTreeModel(root)
                revalidate()
                repaint()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun navigateToLikelyAction(template: PlayTemplateFile) {
        ReadAction.nonBlocking<PsiMethod?> {
            PlayTemplateService.getInstance(project)
                .findLikelyRenderingMethods(template.virtualFile)
                .firstOrNull()
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { action ->
                if (project.isDisposed) {
                    return@finishOnUiThread
                }
                if (action != null) {
                    action.navigate(true)
                } else {
                    OpenFileDescriptor(project, template.virtualFile).navigate(true)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun buildTree(
        templates: List<PlayTemplateFile>,
        tags: List<PlayCustomTagInfo>
    ): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Play templates")

        val templatesRoot = DefaultMutableTreeNode("Templates")
        templates.forEach { template ->
            addPathNode(templatesRoot, template.logicalPath.split('/'), TemplateNode.TemplateLeaf(template))
        }
        root.add(templatesRoot)

        val tagsRoot = DefaultMutableTreeNode("Tags")
        tags.forEach { tag ->
            addPathNode(tagsRoot, tag.qualifiedName.split('.'), TemplateNode.TagLeaf(tag))
        }
        root.add(tagsRoot)

        return root
    }

    private fun addPathNode(
        parent: DefaultMutableTreeNode,
        segments: List<String>,
        leaf: Any
    ) {
        if (segments.isEmpty()) {
            parent.add(DefaultMutableTreeNode(leaf))
            return
        }
        if (segments.size == 1) {
            parent.add(DefaultMutableTreeNode(leaf))
            return
        }

        val segment = segments.first()
        val child = (0 until parent.childCount)
            .map { parent.getChildAt(it) as DefaultMutableTreeNode }
            .firstOrNull { it.userObject == segment }
            ?: DefaultMutableTreeNode(segment).also { parent.add(it) }
        addPathNode(child, segments.drop(1), leaf)
    }
}

private sealed interface TemplateNode {
    data class TemplateLeaf(val template: PlayTemplateFile) : TemplateNode
    data class TagLeaf(val tag: PlayCustomTagInfo) : TemplateNode
}

private class TemplateTreeCellRenderer : DefaultTreeCellRenderer() {

    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        val node = value as? DefaultMutableTreeNode ?: return this
        when (val userObject = node.userObject) {
            is TemplateNode.TemplateLeaf -> {
                text = userObject.template.logicalPath
                toolTipText = buildString {
                    append(userObject.template.logicalPath)
                    userObject.template.controllerName?.let { controller ->
                        append("  →  ")
                        append(controller)
                        userObject.template.actionName?.let { action -> append('.').append(action) }
                    }
                }
                icon = com.intellij.icons.AllIcons.FileTypes.Html
            }
            is TemplateNode.TagLeaf -> {
                text = userObject.tag.qualifiedName
                toolTipText = buildString {
                    append(userObject.tag.logicalPath)
                    if (userObject.tag.parameters.isNotEmpty()) {
                        append("  ·  params: ")
                        append(userObject.tag.parameters.sorted().joinToString(", "))
                    }
                }
                icon = com.intellij.icons.AllIcons.Nodes.Tag
            }
            is String -> {
                toolTipText = null
                if (userObject == "Templates" || userObject == "Tags") {
                    icon = com.intellij.icons.AllIcons.Nodes.Folder
                }
            }
        }
        return this
    }
}

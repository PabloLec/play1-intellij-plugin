package com.github.pablolec.play1toolkit.playjpa.toolwindow

import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaFieldInfo
import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaModelInfo
import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaRelationInfo
import com.github.pablolec.play1toolkit.playjpa.references.PlayJpaFieldUsageSearcher
import com.github.pablolec.play1toolkit.playjpa.references.PlayJpaModelUsageSearcher
import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.ReferencesSearch
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

class PlayJpaModelsPanel(private val project: Project) : JBPanel<PlayJpaModelsPanel>(BorderLayout()) {

    private val summaryLabel = JBLabel("Models: —")
    private val tree = JTree(DefaultMutableTreeNode("Models"))

    init {
        border = JBUI.Borders.emptyTop(4)
        tree.isRootVisible = false
        tree.cellRenderer = PlayJpaModelsTreeRenderer()
        ToolTipManager.sharedInstance().registerComponent(tree)

        add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(summaryLabel, BorderLayout.WEST)
            isOpaque = false
        }, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount < 2) return
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                when (val userObject = node.userObject) {
                    is PlayJpaTreeNode.ModelNode -> userObject.model.psiClass.navigate(true)
                    is PlayJpaTreeNode.FieldNode -> userObject.field.psiField.navigate(true)
                    is PlayJpaTreeNode.RelationNode -> userObject.relation.psiField.navigate(true)
                }
            }
        })

        refresh()
    }

    fun refresh() {
        summaryLabel.text = "Models: loading…"
        ReadAction.nonBlocking<Pair<Int, DefaultMutableTreeNode>> {
            val models = PlayJpaModelService.getInstance(project)
                .getAllModels()
                .sortedBy { it.className.lowercase(Locale.ROOT) }
            Pair(models.size, buildTree(models))
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { (count, root) ->
                summaryLabel.text = "Models: $count"
                tree.model = DefaultTreeModel(root)
                revalidate()
                repaint()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun buildTree(models: List<PlayJpaModelInfo>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Play JPA models")
        models.forEach { model ->
            val usages = ReferencesSearch.search(model.psiClass).findAll().size
            val fixtureUsages = PlayJpaModelUsageSearcher.countFixtureUsages(project, model.psiClass)
            val modelNode = DefaultMutableTreeNode(PlayJpaTreeNode.ModelNode(model, usages, fixtureUsages))

            if (model.idField != null) {
                modelNode.add(DefaultMutableTreeNode(PlayJpaTreeNode.FieldNode(model.idField)))
            }
            model.fields.sortedBy { it.name }.forEach { field ->
                modelNode.add(DefaultMutableTreeNode(PlayJpaTreeNode.FieldNode(field)))
            }
            model.relations.sortedBy { it.fieldName }.forEach { relation ->
                modelNode.add(DefaultMutableTreeNode(PlayJpaTreeNode.RelationNode(relation)))
            }
            root.add(modelNode)
        }
        return root
    }
}

private sealed interface PlayJpaTreeNode {
    data class ModelNode(val model: PlayJpaModelInfo, val usages: Int, val fixtureUsages: Int) : PlayJpaTreeNode
    data class FieldNode(val field: PlayJpaFieldInfo) : PlayJpaTreeNode
    data class RelationNode(val relation: PlayJpaRelationInfo) : PlayJpaTreeNode
}

private class PlayJpaModelsTreeRenderer : DefaultTreeCellRenderer() {
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
            is PlayJpaTreeNode.ModelNode -> {
                text = userObject.model.className
                toolTipText = buildString {
                    append(userObject.model.qualifiedName ?: userObject.model.className)
                    append(" · fields: ")
                    append(userObject.model.fields.joinToString(", ") { it.name })
                    if (userObject.model.relations.isNotEmpty()) {
                        append(" · relations: ")
                        append(userObject.model.relations.joinToString(", ") { "${it.fieldName} → ${it.targetModel ?: "?"}" })
                    }
                    append(" · usages: ${userObject.usages}")
                    append(" · fixtures: ${userObject.fixtureUsages}")
                }
                icon = AllIcons.Nodes.DataTables
            }
            is PlayJpaTreeNode.FieldNode -> {
                text = "${userObject.field.name} : ${userObject.field.typeText}"
                toolTipText = userObject.field.annotations.joinToString(", ").ifBlank { null }
                icon = AllIcons.Nodes.Field
            }
            is PlayJpaTreeNode.RelationNode -> {
                text = "${userObject.relation.fieldName} → ${userObject.relation.targetModel ?: "?"}"
                toolTipText = userObject.relation.relationKind.name.lowercase().replace('_', '-')
                icon = AllIcons.Nodes.Related
            }
        }
        return this
    }
}

package com.github.pablolec.play1toolkit.playjpa.toolwindow

import com.github.pablolec.play1toolkit.playjpa.model.PlayAppModelCategory
import com.github.pablolec.play1toolkit.playjpa.model.PlayAppModelConfidence
import com.github.pablolec.play1toolkit.playjpa.model.PlayAppModelEntry
import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaFieldInfo
import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaRelationInfo
import com.github.pablolec.play1toolkit.playjpa.references.PlayJpaModelUsageSearcher
import com.github.pablolec.play1toolkit.playjpa.service.PlayAppModelClassificationService
import com.github.pablolec.play1toolkit.services.Play1ProjectPaths
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.EnumMap
import java.util.Locale
import javax.swing.JTree
import javax.swing.ToolTipManager
import javax.swing.JEditorPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class PlayJpaModelsPanel(private val project: Project) : JBPanel<PlayJpaModelsPanel>(BorderLayout()) {

    private val summaryLabel = JBLabel("Models: —")
    private val tree = JTree(DefaultMutableTreeNode("Models"))
    private val detailsPane = JEditorPane("text/html", "").apply {
        isEditable = false
        border = JBUI.Borders.empty(8)
    }

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

        val splitter = JBSplitter(false, 0.62f).apply {
            firstComponent = JBScrollPane(tree)
            secondComponent = JBScrollPane(detailsPane)
        }
        add(splitter, BorderLayout.CENTER)

        tree.addTreeSelectionListener {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            detailsPane.text = renderDetails(node.userObject)
            detailsPane.caretPosition = 0
        }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount < 2) return
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                when (val userObject = node.userObject) {
                    is PlayJpaTreeNode.ModelNode -> navigateTo(userObject.entry.psiClass)
                    is PlayJpaTreeNode.FieldNode -> navigateTo(userObject.field.psiField)
                    is PlayJpaTreeNode.RelationNode -> navigateTo(userObject.relation.psiField)
                }
            }
        })

        refresh()
    }

    fun refresh() {
        if (DumbService.isDumb(project)) {
            summaryLabel.text = "Models: 0 (indexing…)"
            detailsPane.text = "<html><body><b>Indexing…</b><br/>The Models view will refresh automatically when indexing finishes.</body></html>"
            DumbService.getInstance(project).runWhenSmart {
                if (!project.isDisposed) {
                    refresh()
                }
            }
            return
        }
        summaryLabel.text = "Models: loading…"
        ReadAction.nonBlocking<ModelsPanelState> {
            runCatching {
                val entries = PlayAppModelClassificationService.getInstance(project)
                    .getAllEntries()
                    .sortedBy { it.className.lowercase(Locale.ROOT) }
                ModelsPanelState(
                    summary = buildSummary(entries),
                    root = buildTree(entries),
                    detailsHtml = buildOverviewDetails(entries)
                )
            }.getOrElse { error ->
                ModelsPanelState(
                    summary = "Models: unavailable",
                    root = DefaultMutableTreeNode("Failed to load models").apply {
                        add(DefaultMutableTreeNode(error.message ?: error.javaClass.simpleName))
                    },
                    detailsHtml = "<html><body><b>Failed to load models.</b><br/>${escapeHtml(error.message ?: error.javaClass.simpleName)}</body></html>"
                )
            }
        }
            .finishOnUiThread(ModalityState.defaultModalityState()) { state ->
                summaryLabel.text = state.summary
                tree.model = DefaultTreeModel(state.root)
                detailsPane.text = state.detailsHtml
                detailsPane.caretPosition = 0
                expandTopLevelNodes()
                revalidate()
                repaint()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun buildTree(entries: List<PlayAppModelEntry>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Application models")
        val grouped = entries.groupBy { it.classification.category }
        val canSearchUsages = !DumbService.isDumb(project)

        CATEGORY_GROUPS.forEach { group ->
            val groupEntries = group.categories.flatMap { grouped[it].orEmpty() }
            if (groupEntries.isEmpty()) return@forEach

            val groupNode = DefaultMutableTreeNode(PlayJpaTreeNode.CategoryNode(group.title, groupEntries.size))
            if (group.subgroups.isNotEmpty()) {
                group.subgroups.forEach { subgroup ->
                    val subgroupEntries = grouped[subgroup.category].orEmpty()
                    if (subgroupEntries.isEmpty()) return@forEach
                    val subgroupNode = DefaultMutableTreeNode(PlayJpaTreeNode.CategoryNode(subgroup.title, subgroupEntries.size))
                    subgroupEntries.forEach { entry ->
                        subgroupNode.add(buildEntryNode(entry, canSearchUsages))
                    }
                    groupNode.add(subgroupNode)
                }
            } else {
                groupEntries.forEach { entry ->
                    groupNode.add(buildEntryNode(entry, canSearchUsages))
                }
            }
            root.add(groupNode)
        }

        return root
    }

    private fun buildEntryNode(entry: PlayAppModelEntry, canSearchUsages: Boolean): DefaultMutableTreeNode {
        val scope = Play1ProjectPaths.applicationScope(project)
        val usages = if (canSearchUsages && entry.persistentModel != null) {
            runCatching {
                if (scope == null) 0 else ReferencesSearch.search(entry.psiClass, scope).findAll().size
            }.getOrDefault(0)
        } else {
            0
        }
        val fixtureUsages = if (entry.persistentModel != null) {
            runCatching { PlayJpaModelUsageSearcher.countFixtureUsages(project, entry.psiClass) }.getOrDefault(0)
        } else {
            0
        }
        val modelNode = DefaultMutableTreeNode(PlayJpaTreeNode.ModelNode(entry, usages, fixtureUsages))

        entry.persistentModel?.idField?.let { modelNode.add(DefaultMutableTreeNode(PlayJpaTreeNode.FieldNode(it))) }
        entry.persistentModel?.fields?.sortedBy { it.name }?.forEach { field ->
            modelNode.add(DefaultMutableTreeNode(PlayJpaTreeNode.FieldNode(field)))
        }
        entry.persistentModel?.relations?.sortedBy { it.fieldName }?.forEach { relation ->
            modelNode.add(DefaultMutableTreeNode(PlayJpaTreeNode.RelationNode(relation)))
        }
        return modelNode
    }

    private fun buildSummary(entries: List<PlayAppModelEntry>): String {
        val persistentCount = entries.count { it.persistentModel != null || it.classification.category in PERSISTENT_CATEGORIES }
        val dtoCount = entries.count { it.classification.category == PlayAppModelCategory.DTO_OR_VIEW_MODEL }
        val businessCount = entries.count { it.classification.category == PlayAppModelCategory.BUSINESS_OBJECT }
        val serviceCount = entries.count { it.classification.category == PlayAppModelCategory.SERVICE_OR_HELPER }
        val enumCount = entries.count { it.classification.category == PlayAppModelCategory.ENUM }
        val unclassifiedCount = entries.count { it.classification.category == PlayAppModelCategory.UNCLASSIFIED }
        return "Models: ${entries.size} · persistent: $persistentCount · dto: $dtoCount · business: $businessCount · services: $serviceCount · enums: $enumCount · unclassified: $unclassifiedCount"
    }

    private fun buildOverviewDetails(entries: List<PlayAppModelEntry>): String {
        val counts = EnumMap<PlayAppModelCategory, Int>(PlayAppModelCategory::class.java)
        entries.forEach { entry ->
            counts.compute(entry.classification.category) { _, current -> (current ?: 0) + 1 }
        }
        return buildString {
            append("<html><body>")
            append("<b>Models overview</b><br/><br/>")
            append("This view classifies the contents of <code>app/models</code>.<br/>")
            append("Persistent JPA models remain a subset used by the Play JPA features.<br/><br/>")
            CATEGORY_GROUPS.forEach { group ->
                val count = group.categories.sumOf { counts[it] ?: 0 }
                if (count == 0) return@forEach
                append("<b>${escapeHtml(group.title)}</b>: $count<br/>")
            }
            append("</body></html>")
        }
    }

    private fun renderDetails(userObject: Any?): String {
        return when (userObject) {
            is PlayJpaTreeNode.CategoryNode -> {
                "<html><body><b>${escapeHtml(userObject.title)}</b><br/>Classes: ${userObject.count}</body></html>"
            }
            is PlayJpaTreeNode.ModelNode -> buildModelDetails(userObject)
            is PlayJpaTreeNode.FieldNode -> buildFieldDetails(userObject.field)
            is PlayJpaTreeNode.RelationNode -> buildRelationDetails(userObject.relation)
            else -> "<html><body><b>Models</b></body></html>"
        }
    }

    private fun buildModelDetails(node: PlayJpaTreeNode.ModelNode): String {
        val entry = node.entry
        val classification = entry.classification
        return buildString {
            append("<html><body>")
            append("<b>${escapeHtml(entry.className)}</b><br/><br/>")
            append("<b>Category</b><br/>${escapeHtml(categoryLabel(classification.category))}<br/>")
            append("<b>Confidence</b><br/>${escapeHtml(confidenceLabel(classification.confidence))}<br/><br/>")
            append("<b>Classification reasons</b><br/>")
            classification.reasons.forEach { append("• ${escapeHtml(it)}<br/>") }
            append("<br/>")
            append("<b>Structure</b><br/>")
            append("Fields: ${entry.fieldCount}<br/>")
            append("Methods: ${entry.methodCount}<br/>")
            if (entry.enumConstantCount > 0) {
                append("Enum constants: ${entry.enumConstantCount}<br/>")
            }
            val modelInfo = entry.persistentModel
            if (modelInfo != null) {
                append("<br/><b>Persistence</b><br/>")
                append("Model kind: ${escapeHtml(modelInfo.sourceKind.name.lowercase().replace('_', ' '))}<br/>")
                modelInfo.idField?.let {
                    append("Primary key: ${escapeHtml(it.name)} : ${escapeHtml(it.typeText)}<br/>")
                }
                if (modelInfo.fields.isNotEmpty()) {
                    append("Columns: ${modelInfo.fields.joinToString(", ") { field -> escapeHtml(field.name) }}<br/>")
                }
                if (modelInfo.relations.isNotEmpty()) {
                    append("Relations: ${modelInfo.relations.joinToString(", ") { rel -> "${escapeHtml(rel.fieldName)} → ${escapeHtml(rel.targetModel ?: "?")}" }}<br/>")
                }
                append("Java usages: ${node.usages}<br/>")
                append("Fixtures: ${node.fixtureUsages}<br/>")
            }
            append("</body></html>")
        }
    }

    private fun buildFieldDetails(field: PlayJpaFieldInfo): String {
        return buildString {
            append("<html><body>")
            append("<b>${escapeHtml(field.name)}</b><br/>")
            append("${escapeHtml(field.typeText)}<br/><br/>")
            if (field.annotations.isNotEmpty()) {
                append("<b>Annotations</b><br/>")
                field.annotations.forEach { append("• ${escapeHtml(it)}<br/>") }
            }
            append("</body></html>")
        }
    }

    private fun buildRelationDetails(relation: PlayJpaRelationInfo): String {
        return buildString {
            append("<html><body>")
            append("<b>${escapeHtml(relation.fieldName)}</b><br/>")
            append("Target: ${escapeHtml(relation.targetModel ?: "?")}<br/>")
            append("Relation: ${escapeHtml(relation.relationKind.name.lowercase().replace('_', '-'))}<br/>")
            append("</body></html>")
        }
    }

    private fun expandTopLevelNodes() {
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }

    private fun navigateTo(element: PsiElement) {
        ReadAction.nonBlocking<OpenFileDescriptor?> {
            val file = element.containingFile?.virtualFile ?: return@nonBlocking null
            OpenFileDescriptor(project, file, element.textOffset)
        }
            .finishOnUiThread(ModalityState.defaultModalityState()) { descriptor ->
                if (descriptor != null && !project.isDisposed) {
                    descriptor.navigate(true)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun categoryLabel(category: PlayAppModelCategory): String = when (category) {
        PlayAppModelCategory.PERSISTENT_PLAY_MODEL -> "Persistent Play Model"
        PlayAppModelCategory.PERSISTENT_GENERIC_MODEL -> "Persistent GenericModel"
        PlayAppModelCategory.JPA_ENTITY -> "JPA Entity"
        PlayAppModelCategory.MAPPED_SUPERCLASS -> "Mapped Superclass"
        PlayAppModelCategory.EMBEDDABLE -> "Embeddable"
        PlayAppModelCategory.DTO_OR_VIEW_MODEL -> "DTO / View Model"
        PlayAppModelCategory.BUSINESS_OBJECT -> "Business Object"
        PlayAppModelCategory.SERVICE_OR_HELPER -> "Service / Helper"
        PlayAppModelCategory.ENUM -> "Enum"
        PlayAppModelCategory.UNCLASSIFIED -> "Unclassified"
    }

    private fun confidenceLabel(confidence: PlayAppModelConfidence): String =
        confidence.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

private data class ModelsPanelState(
    val summary: String,
    val root: DefaultMutableTreeNode,
    val detailsHtml: String
)

private sealed interface PlayJpaTreeNode {
    data class CategoryNode(val title: String, val count: Int) : PlayJpaTreeNode {
        override fun toString(): String = "$title ($count)"
    }

    data class ModelNode(val entry: PlayAppModelEntry, val usages: Int, val fixtureUsages: Int) : PlayJpaTreeNode {
        override fun toString(): String = entry.className
    }

    data class FieldNode(val field: PlayJpaFieldInfo) : PlayJpaTreeNode {
        override fun toString(): String = field.name
    }

    data class RelationNode(val relation: PlayJpaRelationInfo) : PlayJpaTreeNode {
        override fun toString(): String = relation.fieldName
    }
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
            is PlayJpaTreeNode.CategoryNode -> {
                text = "${userObject.title} (${userObject.count})"
                toolTipText = "${userObject.title}: ${userObject.count}"
                icon = AllIcons.Nodes.Folder
            }
            is PlayJpaTreeNode.ModelNode -> {
                val entry = userObject.entry
                text = "${entry.className} · ${entry.classification.confidence.name.lowercase().replaceFirstChar { it.uppercase() }}"
                toolTipText = buildString {
                    append(categoryLabel(entry.classification.category))
                    append(" · confidence: ")
                    append(entry.classification.confidence.name.lowercase())
                    if (entry.classification.reasons.isNotEmpty()) {
                        append(" · ")
                        append(entry.classification.reasons.joinToString("; "))
                    }
                }
                icon = when (entry.classification.category) {
                    PlayAppModelCategory.PERSISTENT_PLAY_MODEL,
                    PlayAppModelCategory.PERSISTENT_GENERIC_MODEL,
                    PlayAppModelCategory.JPA_ENTITY,
                    PlayAppModelCategory.MAPPED_SUPERCLASS,
                    PlayAppModelCategory.EMBEDDABLE -> AllIcons.Nodes.DataTables
                    PlayAppModelCategory.DTO_OR_VIEW_MODEL -> AllIcons.Nodes.Class
                    PlayAppModelCategory.BUSINESS_OBJECT -> AllIcons.Nodes.Class
                    PlayAppModelCategory.SERVICE_OR_HELPER -> AllIcons.Nodes.AbstractMethod
                    PlayAppModelCategory.ENUM -> AllIcons.Nodes.Enum
                    PlayAppModelCategory.UNCLASSIFIED -> AllIcons.Nodes.Class
                }
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

    private fun categoryLabel(category: PlayAppModelCategory): String = when (category) {
        PlayAppModelCategory.PERSISTENT_PLAY_MODEL -> "Persistent Play Model"
        PlayAppModelCategory.PERSISTENT_GENERIC_MODEL -> "Persistent GenericModel"
        PlayAppModelCategory.JPA_ENTITY -> "JPA Entity"
        PlayAppModelCategory.MAPPED_SUPERCLASS -> "Mapped Superclass"
        PlayAppModelCategory.EMBEDDABLE -> "Embeddable"
        PlayAppModelCategory.DTO_OR_VIEW_MODEL -> "DTO / View Model"
        PlayAppModelCategory.BUSINESS_OBJECT -> "Business Object"
        PlayAppModelCategory.SERVICE_OR_HELPER -> "Service / Helper"
        PlayAppModelCategory.ENUM -> "Enum"
        PlayAppModelCategory.UNCLASSIFIED -> "Unclassified"
    }
}

private data class CategoryGroup(
    val title: String,
    val categories: List<PlayAppModelCategory>,
    val subgroups: List<CategorySubgroup> = emptyList()
)

private data class CategorySubgroup(
    val title: String,
    val category: PlayAppModelCategory
)

private val PERSISTENT_CATEGORIES = setOf(
    PlayAppModelCategory.PERSISTENT_PLAY_MODEL,
    PlayAppModelCategory.PERSISTENT_GENERIC_MODEL,
    PlayAppModelCategory.JPA_ENTITY,
    PlayAppModelCategory.MAPPED_SUPERCLASS,
    PlayAppModelCategory.EMBEDDABLE
)

private val CATEGORY_GROUPS = listOf(
    CategoryGroup(
        title = "Persistent Models",
        categories = listOf(
            PlayAppModelCategory.PERSISTENT_PLAY_MODEL,
            PlayAppModelCategory.PERSISTENT_GENERIC_MODEL,
            PlayAppModelCategory.JPA_ENTITY,
            PlayAppModelCategory.MAPPED_SUPERCLASS,
            PlayAppModelCategory.EMBEDDABLE
        ),
        subgroups = listOf(
            CategorySubgroup("Play Model", PlayAppModelCategory.PERSISTENT_PLAY_MODEL),
            CategorySubgroup("Play GenericModel", PlayAppModelCategory.PERSISTENT_GENERIC_MODEL),
            CategorySubgroup("JPA Entity", PlayAppModelCategory.JPA_ENTITY),
            CategorySubgroup("Mapped Superclass", PlayAppModelCategory.MAPPED_SUPERCLASS),
            CategorySubgroup("Embeddable", PlayAppModelCategory.EMBEDDABLE)
        )
    ),
    CategoryGroup("DTOs / View Models", listOf(PlayAppModelCategory.DTO_OR_VIEW_MODEL)),
    CategoryGroup("Business Objects", listOf(PlayAppModelCategory.BUSINESS_OBJECT)),
    CategoryGroup("Services / Helpers", listOf(PlayAppModelCategory.SERVICE_OR_HELPER)),
    CategoryGroup("Enums", listOf(PlayAppModelCategory.ENUM)),
    CategoryGroup("Unclassified", listOf(PlayAppModelCategory.UNCLASSIFIED))
)

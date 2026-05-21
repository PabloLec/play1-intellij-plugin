package com.github.pablolec.play1toolkit.playjobs.toolwindow

import com.github.pablolec.play1toolkit.playjobs.model.PlayJobCategory
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobConfidence
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInfo
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocation
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobTriggerKind
import com.github.pablolec.play1toolkit.playjobs.service.PlayJobService
import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.intellij.find.FindManager
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.JEditorPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class PlayJobsPanel(private val project: Project) : JBPanel<PlayJobsPanel>(BorderLayout()) {

    private val summaryLabel = JBLabel("Jobs: —")
    private val tree = JTree(DefaultMutableTreeNode("Jobs"))
    private val detailsPane = JEditorPane("text/html", "").apply {
        isEditable = false
        border = JBUI.Borders.empty(8)
    }

    init {
        border = JBUI.Borders.emptyTop(4)
        tree.isRootVisible = false
        tree.cellRenderer = PlayJobsTreeRenderer()
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
            override fun mousePressed(e: MouseEvent) = handleMouse(e)
            override fun mouseReleased(e: MouseEvent) = handleMouse(e)
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount >= 2) {
                    val node = nodeAt(e) ?: return
                    when (val userObject = node.userObject) {
                        is PlayJobsTreeNode.JobNode -> navigateTo(userObject.entry.info.psiClass)
                        is PlayJobsTreeNode.TriggerNode -> navigateTo(userObject.psiElement)
                        is PlayJobsTreeNode.MethodNode -> navigateTo(userObject.psiElement)
                        is PlayJobsTreeNode.InvocationNode -> navigateTo(userObject.invocation.psiNewExpression)
                    }
                }
            }
        })

        refresh()
    }

    fun refresh() {
        if (DumbService.isDumb(project)) {
            summaryLabel.text = "Jobs: 0 (indexing…)"
            detailsPane.text = "<html><body><b>Indexing…</b><br/>The Jobs view will refresh automatically when indexing finishes.</body></html>"
            DumbService.getInstance(project).runWhenSmart {
                if (!project.isDisposed) {
                    refresh()
                }
            }
            return
        }
        summaryLabel.text = "Jobs: loading…"
        ReadAction.nonBlocking<JobsPanelState> {
            runCatching {
                val service = PlayJobService.getInstance(project)
                val entries = service.getAllJobs()
                    .sortedBy { it.className.lowercase(Locale.ROOT) }
                    .map { info ->
                        val invocations = runCatching { service.findInvocations(info) }.getOrDefault(emptyList())
                        JobEntry(info, invocations)
                    }
                JobsPanelState(
                    summary = buildSummary(entries),
                    root = buildTree(entries),
                    detailsHtml = buildOverviewDetails(entries)
                )
            }.getOrElse { error ->
                JobsPanelState(
                    summary = "Jobs: unavailable",
                    root = DefaultMutableTreeNode("Failed to load jobs").apply {
                        add(DefaultMutableTreeNode(error.message ?: error.javaClass.simpleName))
                    },
                    detailsHtml = "<html><body><b>Failed to load jobs.</b><br/>${escapeHtml(error.message ?: error.javaClass.simpleName)}</body></html>"
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

    private fun handleMouse(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val node = nodeAt(e) ?: return
        val entry = (node.userObject as? PlayJobsTreeNode.JobNode)?.entry ?: return
        showPopupForJob(entry, e)
    }

    private fun nodeAt(e: MouseEvent): DefaultMutableTreeNode? {
        val path = tree.getPathForLocation(e.x, e.y) ?: return null
        return path.lastPathComponent as? DefaultMutableTreeNode
    }

    private fun showPopupForJob(entry: JobEntry, e: MouseEvent) {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Open Class", null, AllIcons.Actions.EditSource) {
                override fun actionPerformed(ev: AnActionEvent) = navigateTo(entry.info.psiClass)
            })
            entry.info.executionMethods.firstOrNull()?.let { method ->
                add(object : AnAction("Open ${method.name}()", null, AllIcons.Nodes.Method) {
                    override fun actionPerformed(ev: AnActionEvent) = navigateTo(method.psiMethod)
                })
            }
            add(object : AnAction("Copy Job Name", null, AllIcons.Actions.Copy) {
                override fun actionPerformed(ev: AnActionEvent) {
                    CopyPasteManager.getInstance().setContents(StringSelection(entry.info.qualifiedName ?: entry.info.className))
                }
            })
            add(object : AnAction("Find Invocations", null, AllIcons.Actions.Find) {
                override fun actionPerformed(ev: AnActionEvent) {
                    if (!project.isDisposed) {
                        FindManager.getInstance(project).findUsages(entry.info.psiClass)
                    }
                }
            })
        }
        val popup: ActionPopupMenu = ActionManager.getInstance().createActionPopupMenu("PlayJobsToolWindow", group)
        popup.component.show(e.component, e.x, e.y)
    }

    private fun buildTree(entries: List<JobEntry>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Application jobs")
        val grouped = entries.groupBy { it.info.category }

        CATEGORY_GROUPS.forEach { group ->
            val groupEntries = group.categories.flatMap { grouped[it].orEmpty() }
            if (groupEntries.isEmpty()) return@forEach
            val groupNode = DefaultMutableTreeNode(PlayJobsTreeNode.CategoryNode(group.title, groupEntries.size))

            if (group.subgroups.isNotEmpty()) {
                group.subgroups.forEach { subgroup ->
                    val sub = grouped[subgroup.category].orEmpty()
                    if (sub.isEmpty()) return@forEach
                    val subgroupNode = DefaultMutableTreeNode(PlayJobsTreeNode.CategoryNode(subgroup.title, sub.size))
                    sub.forEach { entry -> subgroupNode.add(buildJobNode(entry)) }
                    groupNode.add(subgroupNode)
                }
            } else {
                groupEntries.forEach { entry -> groupNode.add(buildJobNode(entry)) }
            }
            root.add(groupNode)
        }

        return root
    }

    private fun buildJobNode(entry: JobEntry): DefaultMutableTreeNode {
        val jobNode = DefaultMutableTreeNode(PlayJobsTreeNode.JobNode(entry))
        entry.info.triggers.forEach { trigger ->
            val label = formatTrigger(trigger.kind, trigger.rawValue, trigger.async)
            jobNode.add(DefaultMutableTreeNode(PlayJobsTreeNode.TriggerNode(label, trigger.psiAnnotation)))
        }
        entry.info.executionMethods.forEach { method ->
            jobNode.add(DefaultMutableTreeNode(PlayJobsTreeNode.MethodNode("${method.name}()", method.psiMethod)))
        }
        if (entry.invocations.isNotEmpty()) {
            val invNode = DefaultMutableTreeNode(PlayJobsTreeNode.CategoryNode("Manual invocations", entry.invocations.size))
            entry.invocations.forEach { inv ->
                invNode.add(DefaultMutableTreeNode(PlayJobsTreeNode.InvocationNode(inv)))
            }
            jobNode.add(invNode)
        }
        return jobNode
    }

    private fun buildSummary(entries: List<JobEntry>): String {
        val startup = entries.count { it.info.category == PlayJobCategory.STARTUP }
        val scheduled = entries.count { it.info.category == PlayJobCategory.SCHEDULED_EVERY || it.info.category == PlayJobCategory.SCHEDULED_CRON }
        val shutdown = entries.count { it.info.category == PlayJobCategory.SHUTDOWN }
        val manual = entries.count { it.info.category == PlayJobCategory.MANUAL_ASYNC }
        val unknown = entries.count { it.info.category == PlayJobCategory.UNKNOWN }
        return "Jobs: ${entries.size} · startup: $startup · scheduled: $scheduled · shutdown: $shutdown · manual: $manual · unknown: $unknown"
    }

    private fun buildOverviewDetails(entries: List<JobEntry>): String {
        return buildString {
            append("<html><body>")
            append("<b>Jobs overview</b><br/><br/>")
            append("This view lists every non-HTTP runtime hook detected in the project: ")
            append("startup / shutdown hooks, scheduled tasks and manual <code>new SomeJob().now()</code> invocations.<br/><br/>")
            CATEGORY_GROUPS.forEach { group ->
                val count = entries.count { it.info.category in group.categories }
                if (count == 0) return@forEach
                append("<b>${escapeHtml(group.title)}</b>: $count<br/>")
            }
            append("</body></html>")
        }
    }

    private fun renderDetails(userObject: Any?): String = when (userObject) {
        is PlayJobsTreeNode.CategoryNode ->
            "<html><body><b>${escapeHtml(userObject.title)}</b><br/>Items: ${userObject.count}</body></html>"
        is PlayJobsTreeNode.JobNode -> renderJobDetails(userObject.entry)
        is PlayJobsTreeNode.TriggerNode ->
            "<html><body><b>${escapeHtml(userObject.label)}</b></body></html>"
        is PlayJobsTreeNode.MethodNode ->
            "<html><body><b>${escapeHtml(userObject.label)}</b></body></html>"
        is PlayJobsTreeNode.InvocationNode -> renderInvocationDetails(userObject.invocation)
        else -> "<html><body><b>Jobs</b></body></html>"
    }

    private fun renderJobDetails(entry: JobEntry): String {
        val info = entry.info
        return buildString {
            append("<html><body>")
            append("<b>${escapeHtml(info.className)}</b><br/><br/>")
            append("<b>Category</b><br/>${escapeHtml(categoryLabel(info.category))}<br/>")
            append("<b>Confidence</b><br/>${escapeHtml(confidenceLabel(info.confidence))}<br/><br/>")
            if (info.reasons.isNotEmpty()) {
                append("<b>Reasons</b><br/>")
                info.reasons.forEach { append("• ${escapeHtml(it)}<br/>") }
                append("<br/>")
            }
            if (info.triggers.isNotEmpty()) {
                append("<b>Triggers</b><br/>")
                info.triggers.forEach { trigger ->
                    append("• ${escapeHtml(formatTrigger(trigger.kind, trigger.rawValue, trigger.async))}<br/>")
                }
                append("<br/>")
            }
            if (info.executionMethods.isNotEmpty()) {
                append("<b>Execution methods</b><br/>")
                info.executionMethods.forEach { method ->
                    append("• ${escapeHtml(method.name)}() ${if (method.returnsResult) "→ result" else ""}<br/>")
                }
                append("<br/>")
            }
            append("<b>Manual invocations</b>: ${entry.invocations.size}<br/>")
            append("</body></html>")
        }
    }

    private fun renderInvocationDetails(invocation: PlayJobInvocation): String {
        val kindLabel = when (invocation.kind) {
            com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocationKind.NOW -> "now()"
            com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocationKind.IN -> "in(\"…\")"
            com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocationKind.AT -> "at(\"…\")"
            com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocationKind.AFTER_REQUEST -> "afterRequest()"
            com.github.pablolec.play1toolkit.playjobs.model.PlayJobInvocationKind.NEW_ONLY -> "new only"
        }
        return buildString {
            append("<html><body>")
            append("<b>Manual invocation</b><br/>")
            append("Form: ${escapeHtml(kindLabel)}<br/>")
            append("Code: <code>${escapeHtml(invocation.callChain)}</code><br/>")
            append("</body></html>")
        }
    }

    private fun formatTrigger(kind: PlayJobTriggerKind, raw: String?, async: Boolean): String {
        val simple = PlayJobUtils.triggerSimpleName(kind)
        val base = if (raw.isNullOrBlank()) "@$simple" else "@$simple(\"$raw\")"
        return if (async) "$base (async)" else base
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

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    @Suppress("unused")
    private fun openFileFallback(element: PsiElement) {
        val support = PsiNavigationSupport.getInstance()
        if (support.canNavigate(element)) support.getDescriptor(element)?.navigate(true)
    }
}

private data class JobEntry(
    val info: PlayJobInfo,
    val invocations: List<PlayJobInvocation>
)

private data class JobsPanelState(
    val summary: String,
    val root: DefaultMutableTreeNode,
    val detailsHtml: String
)

private sealed interface PlayJobsTreeNode {
    data class CategoryNode(val title: String, val count: Int) : PlayJobsTreeNode {
        override fun toString(): String = "$title ($count)"
    }
    data class JobNode(val entry: JobEntry) : PlayJobsTreeNode {
        override fun toString(): String = entry.info.className
    }
    data class TriggerNode(val label: String, val psiElement: PsiElement) : PlayJobsTreeNode {
        override fun toString(): String = label
    }
    data class MethodNode(val label: String, val psiElement: PsiElement) : PlayJobsTreeNode {
        override fun toString(): String = label
    }
    data class InvocationNode(val invocation: PlayJobInvocation) : PlayJobsTreeNode {
        override fun toString(): String = invocation.callChain
    }
}

private class PlayJobsTreeRenderer : DefaultTreeCellRenderer() {
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
            is PlayJobsTreeNode.CategoryNode -> {
                text = "${userObject.title} (${userObject.count})"
                toolTipText = "${userObject.title}: ${userObject.count}"
                icon = AllIcons.Nodes.Folder
            }
            is PlayJobsTreeNode.JobNode -> {
                val entry = userObject.entry
                val invocationSuffix = if (entry.invocations.isEmpty()) "" else " · ${entry.invocations.size} invocation${if (entry.invocations.size > 1) "s" else ""}"
                text = "${entry.info.className} · ${confidenceLabel(entry.info.confidence)}$invocationSuffix"
                toolTipText = entry.info.reasons.joinToString("; ").ifBlank { categoryLabel(entry.info.category) }
                icon = when (entry.info.category) {
                    PlayJobCategory.STARTUP -> AllIcons.Actions.Execute
                    PlayJobCategory.SHUTDOWN -> AllIcons.Actions.Cancel
                    PlayJobCategory.SCHEDULED_EVERY -> AllIcons.Vcs.History
                    PlayJobCategory.SCHEDULED_CRON -> AllIcons.Vcs.History
                    PlayJobCategory.MANUAL_ASYNC -> AllIcons.Actions.RunAll
                    PlayJobCategory.UNKNOWN -> AllIcons.General.Information
                }
            }
            is PlayJobsTreeNode.TriggerNode -> {
                text = userObject.label
                icon = AllIcons.Nodes.Annotationtype
            }
            is PlayJobsTreeNode.MethodNode -> {
                text = userObject.label
                icon = AllIcons.Nodes.Method
            }
            is PlayJobsTreeNode.InvocationNode -> {
                text = userObject.invocation.callChain
                icon = AllIcons.Actions.RunAll
            }
        }
        return this
    }
}

private fun categoryLabel(category: PlayJobCategory): String = when (category) {
    PlayJobCategory.STARTUP -> "Startup"
    PlayJobCategory.SHUTDOWN -> "Shutdown"
    PlayJobCategory.SCHEDULED_EVERY -> "Scheduled · every"
    PlayJobCategory.SCHEDULED_CRON -> "Scheduled · cron"
    PlayJobCategory.MANUAL_ASYNC -> "Manual / async"
    PlayJobCategory.UNKNOWN -> "Unknown scheduling"
}

private fun confidenceLabel(confidence: PlayJobConfidence): String =
    confidence.name.lowercase().replaceFirstChar { it.uppercase() }

private data class JobsCategoryGroup(
    val title: String,
    val categories: List<PlayJobCategory>,
    val subgroups: List<JobsCategorySubgroup> = emptyList()
)

private data class JobsCategorySubgroup(
    val title: String,
    val category: PlayJobCategory
)

private val CATEGORY_GROUPS = listOf(
    JobsCategoryGroup("Startup", listOf(PlayJobCategory.STARTUP)),
    JobsCategoryGroup(
        "Scheduled",
        listOf(PlayJobCategory.SCHEDULED_EVERY, PlayJobCategory.SCHEDULED_CRON),
        subgroups = listOf(
            JobsCategorySubgroup("Every", PlayJobCategory.SCHEDULED_EVERY),
            JobsCategorySubgroup("Cron", PlayJobCategory.SCHEDULED_CRON)
        )
    ),
    JobsCategoryGroup("Shutdown", listOf(PlayJobCategory.SHUTDOWN)),
    JobsCategoryGroup("Manual / Async", listOf(PlayJobCategory.MANUAL_ASYNC)),
    JobsCategoryGroup("Unknown scheduling", listOf(PlayJobCategory.UNKNOWN))
)

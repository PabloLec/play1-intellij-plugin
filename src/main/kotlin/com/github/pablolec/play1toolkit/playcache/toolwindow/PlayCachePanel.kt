package com.github.pablolec.play1toolkit.playcache.toolwindow

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsage
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheUsageKind
import com.github.pablolec.play1toolkit.playcache.model.PlayCachedActionInfo
import com.github.pablolec.play1toolkit.playcache.model.PlayCachedTemplateFragment
import com.github.pablolec.play1toolkit.playcache.service.PlayCacheService
import com.github.pablolec.play1toolkit.playcache.util.PlayCacheTemplateValueResolver
import com.github.pablolec.play1toolkit.response.PlayResponsePresentation
import com.intellij.find.FindManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
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
import javax.swing.JEditorPane
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class PlayCachePanel(private val project: Project) : JBPanel<PlayCachePanel>(BorderLayout()) {

    private val summaryLabel = JBLabel("Cache: —")
    private val tree = JTree(DefaultMutableTreeNode("Cache"))
    private val detailsPane = JEditorPane("text/html", "").apply {
        isEditable = false
        border = JBUI.Borders.empty(8)
    }

    init {
        border = JBUI.Borders.emptyTop(4)
        tree.isRootVisible = false
        tree.cellRenderer = PlayCacheTreeRenderer()
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
            detailsPane.text = ApplicationManager.getApplication().runReadAction<String> {
                renderDetails(node.userObject)
            }
            detailsPane.caretPosition = 0
        }

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = handleMouse(e)
            override fun mouseReleased(e: MouseEvent) = handleMouse(e)
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount >= 2) {
                    val node = nodeAt(e) ?: return
                    when (val obj = node.userObject) {
                        is CacheTreeNode.UsageNode -> navigateTo(obj.usage.sourceElement)
                        is CacheTreeNode.CachedActionNode -> navigateTo(obj.info.actionMethod)
                        is CacheTreeNode.TemplateFragmentNode -> navigateTo(obj.fragment)
                        is CacheTreeNode.StaticKeyNode -> obj.firstUsage?.let { navigateTo(it.sourceElement) }
                        else -> Unit
                    }
                }
            }
        })

        refresh()
    }

    fun refresh() {
        if (DumbService.isDumb(project)) {
            summaryLabel.text = "Cache: 0 (indexing…)"
            detailsPane.text = "<html><body><b>Indexing…</b><br/>The Cache view will refresh automatically when indexing finishes.</body></html>"
            DumbService.getInstance(project).runWhenSmart {
                if (!project.isDisposed) refresh()
            }
            return
        }
        summaryLabel.text = "Cache: loading…"
        ReadAction.nonBlocking<CachePanelState> {
            runCatching {
                val service = PlayCacheService.getInstance(project)
                val usages = service.getAllUsages()
                val fragments = service.getTemplateFragments()
                val actions = service.getCachedActions()
                CachePanelState(
                    summary = buildSummary(usages, fragments, actions),
                    root = buildTree(usages, fragments, actions),
                    detailsHtml = buildOverview(usages, fragments, actions)
                )
            }.getOrElse { error ->
                CachePanelState(
                    summary = "Cache: unavailable",
                    root = DefaultMutableTreeNode("Failed to load cache data").apply {
                        add(DefaultMutableTreeNode(error.message ?: error.javaClass.simpleName))
                    },
                    detailsHtml = "<html><body><b>Failed to load cache data.</b><br/>${escapeHtml(error.message ?: error.javaClass.simpleName)}</body></html>"
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
        when (val obj = node.userObject) {
            is CacheTreeNode.StaticKeyNode -> showPopupForKey(obj, e)
            is CacheTreeNode.UsageNode -> showPopupForUsage(obj, e)
            else -> Unit
        }
    }

    private fun nodeAt(e: MouseEvent): DefaultMutableTreeNode? {
        val path = tree.getPathForLocation(e.x, e.y) ?: return null
        return path.lastPathComponent as? DefaultMutableTreeNode
    }

    private fun showPopupForKey(obj: CacheTreeNode.StaticKeyNode, e: MouseEvent) {
        val group = DefaultActionGroup().apply {
            obj.firstUsage?.let { usage ->
                add(object : AnAction("Open Source", null, AllIcons.Actions.EditSource) {
                    override fun actionPerformed(ev: AnActionEvent) = navigateTo(usage.sourceElement)
                })
            }
            add(object : AnAction("Copy Key", null, AllIcons.Actions.Copy) {
                override fun actionPerformed(ev: AnActionEvent) {
                    CopyPasteManager.getInstance().setContents(StringSelection(obj.key))
                }
            })
            obj.firstUsage?.sourceElement?.let { source ->
                add(object : AnAction("Find Related Cache Usages", null, AllIcons.Actions.Find) {
                    override fun actionPerformed(ev: AnActionEvent) {
                        if (!project.isDisposed) {
                            FindManager.getInstance(project).findUsages(source)
                        }
                    }
                })
            }
        }
        val popup: ActionPopupMenu = ActionManager.getInstance().createActionPopupMenu("PlayCacheToolWindow", group)
        popup.component.show(e.component, e.x, e.y)
    }

    private fun showPopupForUsage(obj: CacheTreeNode.UsageNode, e: MouseEvent) {
        val group = DefaultActionGroup().apply {
            add(object : AnAction("Open Source", null, AllIcons.Actions.EditSource) {
                override fun actionPerformed(ev: AnActionEvent) = navigateTo(obj.usage.sourceElement)
            })
        }
        val popup: ActionPopupMenu = ActionManager.getInstance().createActionPopupMenu("PlayCacheToolWindow", group)
        popup.component.show(e.component, e.x, e.y)
    }

    private fun buildTree(
        usages: List<PlayCacheUsage>,
        fragments: List<PlayCachedTemplateFragment>,
        actions: List<PlayCachedActionInfo>
    ): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Cache")

        if (fragments.isNotEmpty()) {
            val node = DefaultMutableTreeNode(CacheTreeNode.SectionNode("Template fragments", fragments.size))
            fragments.forEach { fragment ->
                val label = fragment.templateFile.virtualFile?.path?.substringAfterLast("/app/views/")
                    ?: fragment.templateFile.name
                node.add(DefaultMutableTreeNode(CacheTreeNode.TemplateFragmentNode(label, fragment)))
            }
            root.add(node)
        }

        if (actions.isNotEmpty()) {
            val node = DefaultMutableTreeNode(CacheTreeNode.SectionNode("Cached actions", actions.size))
            actions.forEach { info ->
                val label = "${info.controllerClass.name ?: "?"}.${info.actionMethod.name}"
                node.add(DefaultMutableTreeNode(CacheTreeNode.CachedActionNode(label, info)))
            }
            root.add(node)
        }

        val staticUsages = usages.filter { it.key is PlayCacheKey.Static }
        val byKey = staticUsages.groupBy { (it.key as PlayCacheKey.Static).value }
        if (byKey.isNotEmpty()) {
            val node = DefaultMutableTreeNode(CacheTreeNode.SectionNode("Static keys", byKey.size))
            byKey.entries.sortedBy { it.key }.forEach { (key, list) ->
                val keyNode = DefaultMutableTreeNode(CacheTreeNode.StaticKeyNode(key, list, list.firstOrNull()))
                list.forEach { keyNode.add(DefaultMutableTreeNode(CacheTreeNode.UsageNode(it))) }
                node.add(keyNode)
            }
            root.add(node)
        }

        val dynamic = usages.filter { it.key is PlayCacheKey.Dynamic || it.key is PlayCacheKey.Pattern }
        if (dynamic.isNotEmpty()) {
            val node = DefaultMutableTreeNode(CacheTreeNode.SectionNode("Dynamic usages", dynamic.size))
            dynamic.forEach { node.add(DefaultMutableTreeNode(CacheTreeNode.UsageNode(it))) }
            root.add(node)
        }

        val clears = usages.filter { it.kind == PlayCacheUsageKind.JAVA_CLEAR }
        if (clears.isNotEmpty()) {
            val node = DefaultMutableTreeNode(CacheTreeNode.SectionNode("Global clears", clears.size))
            clears.forEach { node.add(DefaultMutableTreeNode(CacheTreeNode.UsageNode(it))) }
            root.add(node)
        }

        val diagnostics = buildDiagnostics(usages, fragments)
        if (diagnostics.isNotEmpty()) {
            val node = DefaultMutableTreeNode(CacheTreeNode.SectionNode("Diagnostics", diagnostics.size))
            diagnostics.forEach { node.add(DefaultMutableTreeNode(it)) }
            root.add(node)
        }

        return root
    }

    private fun buildDiagnostics(
        usages: List<PlayCacheUsage>,
        fragments: List<PlayCachedTemplateFragment>
    ): List<CacheTreeNode.UsageNode> {
        val out = mutableListOf<CacheTreeNode.UsageNode>()
        val writesWithoutTtl = usages.filter {
            (it.kind == PlayCacheUsageKind.JAVA_WRITE ||
                it.kind == PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT ||
                it.kind == PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT) &&
                it.ttl == PlayCacheTtl.Absent
        }
        writesWithoutTtl.forEach { out += CacheTreeNode.UsageNode(it) }
        val fragmentDiags = fragments
            .filter { it.ttl == PlayCacheTtl.Absent }
            .map { CacheTreeNode.UsageNode(synthesizeFragmentUsage(it)) }
        out += fragmentDiags
        return out
    }

    private fun synthesizeFragmentUsage(fragment: PlayCachedTemplateFragment): PlayCacheUsage =
        PlayCacheUsage(
            kind = PlayCacheUsageKind.TEMPLATE_FRAGMENT,
            key = fragment.key,
            ttl = fragment.ttl,
            sourceElement = fragment.templateFile.findElementAt(fragment.openTagRange.startOffset) ?: fragment.templateFile,
            ownerDescription = fragment.templateFile.virtualFile?.path
                ?.substringAfterLast("/app/views/")
                ?: fragment.templateFile.name,
            containingFile = fragment.templateFile.virtualFile
        )

    private fun buildSummary(
        usages: List<PlayCacheUsage>,
        fragments: List<PlayCachedTemplateFragment>,
        actions: List<PlayCachedActionInfo>
    ): String {
        val staticKeys = usages.mapNotNull { (it.key as? PlayCacheKey.Static)?.value }.distinct().size
        val clears = usages.count { it.kind == PlayCacheUsageKind.JAVA_CLEAR }
        return "Cache: ${fragments.size} fragments · ${actions.size} actions · $staticKeys static keys · $clears clears"
    }

    private fun buildOverview(
        usages: List<PlayCacheUsage>,
        fragments: List<PlayCachedTemplateFragment>,
        actions: List<PlayCachedActionInfo>
    ): String = buildString {
        append("<html><body>")
        append("<b>Cache overview</b><br/><br/>")
        append("This view maps every Play 1 cache usage in the project: template <code>#{cache}</code> fragments,")
        append(" actions annotated <code>@CacheFor</code>, and direct <code>play.cache.Cache</code> calls.<br/><br/>")
        append("Template fragments: ${fragments.size}<br/>")
        append("Cached actions: ${actions.size}<br/>")
        append("Total Java calls: ${usages.count { it.kind != PlayCacheUsageKind.TEMPLATE_FRAGMENT }}<br/>")
        append("Global clears: ${usages.count { it.kind == PlayCacheUsageKind.JAVA_CLEAR }}<br/>")
        append("</body></html>")
    }

    private fun renderDetails(obj: Any?): String = when (obj) {
        is CacheTreeNode.SectionNode ->
            "<html><body><b>${escapeHtml(obj.title)}</b><br/>Items: ${obj.count}</body></html>"
        is CacheTreeNode.TemplateFragmentNode -> renderFragment(obj.fragment)
        is CacheTreeNode.CachedActionNode -> renderCachedAction(obj.info)
        is CacheTreeNode.StaticKeyNode -> renderStaticKey(obj)
        is CacheTreeNode.UsageNode -> renderUsage(obj.usage)
        else -> "<html><body><b>Cache</b></body></html>"
    }

    private fun renderFragment(fragment: PlayCachedTemplateFragment): String = buildString {
        val keyInfo = PlayCacheTemplateValueResolver.resolveKey(project, fragment)
        val ttlInfo = PlayCacheTemplateValueResolver.resolveTtl(project, fragment)
        val guardInfo = PlayCacheTemplateValueResolver.resolveGuard(project, fragment.templateFile)
        append("<html><body>")
        append("<b>Cached template fragment</b><br/><br/>")
        append("<b>Key:</b> ${escapeHtml(keyLabel(fragment.key))}<br/>")
        if (keyInfo.resolvedValue != null || keyInfo.configurationKey != null) {
            append("<b>Resolved key:</b> ${escapeHtml(keyInfo.displayText)}<br/>")
        }
        append("<b>Expiration:</b> ${escapeHtml(ttlLabel(fragment.ttl))}<br/>")
        if (ttlInfo.resolvedValue != null || ttlInfo.configurationKey != null || ttlInfo.configurationValue != null) {
            append("<b>Resolved expiration:</b> ${escapeHtml(ttlInfo.displayText)}<br/>")
        }
        if (guardInfo?.booleanValue != null) {
            val state = if (guardInfo.booleanValue) "enabled" else "disabled"
            val source = guardInfo.configurationKey?.let { " (${it}=${guardInfo.configurationValue ?: "?"})" }.orEmpty()
            append("<b>Cache guard:</b> ${escapeHtml(state + source)}<br/>")
        }
        val tmpl = fragment.templateFile.virtualFile?.path?.substringAfterLast("/app/views/")
            ?: fragment.templateFile.name
        append("<b>Template:</b> ${escapeHtml(tmpl)}<br/>")
        if (fragment.includedTemplatePaths.isNotEmpty()) {
            append("<b>Includes:</b> ${escapeHtml(fragment.includedTemplatePaths.joinToString(", "))}<br/>")
        }
        append("</body></html>")
    }

    private fun renderCachedAction(info: PlayCachedActionInfo): String = buildString {
        append("<html><body>")
        append("<b>Cached action</b><br/><br/>")
        append("<b>Action:</b> ${escapeHtml("${info.controllerClass.name ?: "?"}.${info.actionMethod.name}")}<br/>")
        append("<b>TTL:</b> ${escapeHtml(ttlLabel(info.ttl))}<br/>")
        if (info.routes.isNotEmpty()) {
            val routes = info.routes.joinToString("<br/>") { route ->
                val method = route.getHttpMethod()?.text?.trim().orEmpty()
                val path = route.getPath().orEmpty()
                escapeHtml("$method $path".trim())
            }
            append("<b>Routes:</b> $routes<br/>")
        }
        info.responseInfo?.let { response ->
            append("<b>Response:</b> ${escapeHtml(PlayResponsePresentation.shortLabel(response.kind))}<br/>")
        }
        append("</body></html>")
    }

    private fun renderStaticKey(obj: CacheTreeNode.StaticKeyNode): String {
        val reads = obj.usages.count { it.kind == PlayCacheUsageKind.JAVA_READ || it.kind == PlayCacheUsageKind.JAVA_READ_OR_COMPUTE }
        val writes = obj.usages.count {
            it.kind == PlayCacheUsageKind.JAVA_WRITE ||
                it.kind == PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT ||
                it.kind == PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT
        }
        val invalidations = obj.usages.count { it.kind == PlayCacheUsageKind.JAVA_INVALIDATION }
        return buildString {
            append("<html><body>")
            append("<b>${escapeHtml(obj.key)}</b><br/><br/>")
            append("Reads: $reads<br/>")
            append("Writes: $writes<br/>")
            append("Invalidations: $invalidations<br/>")
            append("</body></html>")
        }
    }

    private fun renderUsage(usage: PlayCacheUsage): String = buildString {
        append("<html><body>")
        append("<b>${escapeHtml(kindLabel(usage.kind))}</b><br/><br/>")
        append("<b>Owner:</b> ${escapeHtml(usage.ownerDescription)}<br/>")
        append("<b>Key:</b> ${escapeHtml(keyLabel(usage.key))}<br/>")
        append("<b>TTL:</b> ${escapeHtml(ttlLabel(usage.ttl))}<br/>")
        usage.keyConfigurationKey?.let { append("<b>Key config:</b> ${escapeHtml(it)}<br/>") }
        usage.ttlConfigurationKey?.let { append("<b>TTL config:</b> ${escapeHtml(it)}<br/>") }
        usage.valueType?.let { append("<b>Value type:</b> ${escapeHtml(it)}<br/>") }
        usage.details?.let { append("<b>Value:</b> <code>${escapeHtml(it)}</code><br/>") }
        append("</body></html>")
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

    private fun navigateTo(fragment: PlayCachedTemplateFragment) {
        ReadAction.nonBlocking<OpenFileDescriptor?> {
            val anchor = fragment.templateFile.findElementAt(fragment.openTagRange.startOffset) ?: fragment.templateFile
            val file = anchor.containingFile?.virtualFile ?: return@nonBlocking null
            OpenFileDescriptor(project, file, anchor.textOffset)
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
}

private data class CachePanelState(
    val summary: String,
    val root: DefaultMutableTreeNode,
    val detailsHtml: String
)

private sealed interface CacheTreeNode {
    data class SectionNode(val title: String, val count: Int) : CacheTreeNode {
        override fun toString(): String = "$title ($count)"
    }
    data class TemplateFragmentNode(val label: String, val fragment: PlayCachedTemplateFragment) : CacheTreeNode {
        override fun toString(): String = label
    }
    data class CachedActionNode(val label: String, val info: PlayCachedActionInfo) : CacheTreeNode {
        override fun toString(): String = label
    }
    data class StaticKeyNode(
        val key: String,
        val usages: List<PlayCacheUsage>,
        val firstUsage: PlayCacheUsage?
    ) : CacheTreeNode {
        override fun toString(): String = "$key (${usages.size})"
    }
    data class UsageNode(val usage: PlayCacheUsage) : CacheTreeNode {
        override fun toString(): String = "${kindShort(usage.kind)} · ${usage.ownerDescription}"
    }
}

private fun kindShort(kind: PlayCacheUsageKind): String = when (kind) {
    PlayCacheUsageKind.JAVA_READ -> "read"
    PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> "read/compute"
    PlayCacheUsageKind.JAVA_WRITE -> "write"
    PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT -> "add"
    PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT -> "replace"
    PlayCacheUsageKind.JAVA_INVALIDATION -> "invalidate"
    PlayCacheUsageKind.JAVA_CLEAR -> "CLEAR"
    PlayCacheUsageKind.JAVA_MUTATION -> "mutate"
    PlayCacheUsageKind.TEMPLATE_FRAGMENT -> "fragment"
    PlayCacheUsageKind.CACHED_ACTION -> "action"
}

private fun kindLabel(kind: PlayCacheUsageKind): String = when (kind) {
    PlayCacheUsageKind.JAVA_READ -> "Cache read"
    PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> "Cache read or compute"
    PlayCacheUsageKind.JAVA_WRITE -> "Cache write"
    PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT -> "Cache add (write if absent)"
    PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT -> "Cache replace (write if present)"
    PlayCacheUsageKind.JAVA_INVALIDATION -> "Cache invalidation"
    PlayCacheUsageKind.JAVA_CLEAR -> "Global cache clear"
    PlayCacheUsageKind.JAVA_MUTATION -> "Cache mutation"
    PlayCacheUsageKind.TEMPLATE_FRAGMENT -> "Template fragment"
    PlayCacheUsageKind.CACHED_ACTION -> "Cached action"
}

private fun keyLabel(key: PlayCacheKey): String = when (key) {
    is PlayCacheKey.Static -> key.value
    is PlayCacheKey.Pattern -> "pattern ${key.value}"
    is PlayCacheKey.Dynamic -> "dynamic ${key.expressionText}"
    PlayCacheKey.Missing -> "—"
}

private fun ttlLabel(ttl: PlayCacheTtl): String = when (ttl) {
    is PlayCacheTtl.Static -> if (ttl.value.isEmpty()) "no expiration" else ttl.value
    is PlayCacheTtl.Dynamic -> "dynamic ${ttl.expressionText}"
    PlayCacheTtl.Absent -> "no expiration"
}

private class PlayCacheTreeRenderer : DefaultTreeCellRenderer() {
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
        when (val obj = node.userObject) {
            is CacheTreeNode.SectionNode -> {
                text = "${obj.title} (${obj.count})"
                icon = AllIcons.Nodes.Folder
            }
            is CacheTreeNode.TemplateFragmentNode -> {
                text = obj.toString()
                icon = AllIcons.FileTypes.Html
            }
            is CacheTreeNode.CachedActionNode -> {
                text = obj.toString()
                icon = AllIcons.Nodes.Method
            }
            is CacheTreeNode.StaticKeyNode -> {
                text = "${obj.key} (${obj.usages.size})"
                icon = AllIcons.Nodes.Tag
            }
            is CacheTreeNode.UsageNode -> {
                text = obj.toString()
                icon = when (obj.usage.kind) {
                    PlayCacheUsageKind.JAVA_READ, PlayCacheUsageKind.JAVA_READ_OR_COMPUTE -> AllIcons.Actions.Find
                    PlayCacheUsageKind.JAVA_WRITE,
                    PlayCacheUsageKind.JAVA_WRITE_IF_ABSENT,
                    PlayCacheUsageKind.JAVA_WRITE_IF_PRESENT -> AllIcons.Actions.MenuSaveall
                    PlayCacheUsageKind.JAVA_INVALIDATION -> AllIcons.Actions.GC
                    PlayCacheUsageKind.JAVA_CLEAR -> AllIcons.General.Warning
                    PlayCacheUsageKind.JAVA_MUTATION -> AllIcons.Actions.Edit
                    PlayCacheUsageKind.TEMPLATE_FRAGMENT -> AllIcons.FileTypes.Html
                    PlayCacheUsageKind.CACHED_ACTION -> AllIcons.Nodes.Method
                }
            }
        }
        return this
    }
}

package com.github.pablolec.play1toolkit.toolwindow

import com.github.pablolec.play1toolkit.routes.psi.RoutesFile
import com.github.pablolec.play1toolkit.routes.psi.RoutesRouteElement
import com.github.pablolec.play1toolkit.services.Play1ProjectPaths
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.BoxLayout
import javax.swing.JPanel

class DiagnosticsPanel(private val project: Project) : JBPanel<DiagnosticsPanel>(BorderLayout()) {

    private val countLabel = JBLabel("Diagnostics: —")
    private val issuesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    init {
        border = JBUI.Borders.emptyTop(4)

        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 8)
            add(countLabel, BorderLayout.WEST)
        }

        add(header, BorderLayout.NORTH)
        add(JBScrollPane(issuesPanel), BorderLayout.CENTER)
        refresh()
    }

    fun refresh() {
        issuesPanel.removeAll()
        countLabel.text = "Diagnostics: checking…"
        revalidate()
        repaint()

        ReadAction.nonBlocking<List<String>> { collectIssues() }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { issues ->
                issuesPanel.removeAll()
                if (issues.isEmpty()) {
                    countLabel.text = "Diagnostics: no issues"
                    issuesPanel.add(JBLabel("  No issues found").apply {
                        border = JBUI.Borders.empty(2, 8)
                    })
                } else {
                    countLabel.text = "Diagnostics: ${issues.size} issue(s)"
                    issues.forEach { msg ->
                        issuesPanel.add(JBLabel("⚠  $msg").apply {
                            border = JBUI.Borders.empty(2, 8)
                        })
                    }
                }
                revalidate()
                repaint()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun collectIssues(): List<String> {
        val issues = mutableListOf<String>()
        val routesFile = findRoutesFile() ?: return issues

        val scope = GlobalSearchScope.projectScope(project)
        val psiFacade = JavaPsiFacade.getInstance(project)
        val cache = PsiShortNamesCache.getInstance(project)

        routesFile.getRoutes().forEach { route ->
            if (!route.isDynamicRoute()) return@forEach

            val ctrlElement = route.getControllerName() ?: return@forEach
            val ctrlName = ctrlElement.text.trim()
            if (ctrlName.isEmpty() || ctrlName.contains('{')) return@forEach

            val psiClass = psiFacade.findClass(ctrlName, scope)
                ?: cache.getClassesByName(ctrlName, scope).firstOrNull()

            if (psiClass == null) {
                issues.add("Controller not found: $ctrlName")
                return@forEach
            }

            val actionElement = route.getActionName() ?: return@forEach
            val actionName = actionElement.text.trim()
            if (actionName.isEmpty()) return@forEach

            if (psiClass.findMethodsByName(actionName, true).isEmpty()) {
                issues.add("Action not found: $ctrlName.$actionName")
            }
        }
        return issues
    }

    private fun findRoutesFile(): RoutesFile? {
        val basePath = Play1ProjectPaths.applicationPath(project) ?: return null
        val vFile = VirtualFileManager.getInstance()
            .findFileByNioPath(Paths.get(basePath, "conf", "routes")) ?: return null
        return PsiManager.getInstance(project).findFile(vFile) as? RoutesFile
    }
}

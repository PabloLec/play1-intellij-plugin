package com.github.pablolec.play1toolkit.playconfig.documentation

import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.references.PlayConfigContextDetector
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigKnownKeys
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.project.DumbService
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.SmartPointerManager

class PlayConfigDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (DumbService.isDumb(file.project)) return emptyList()
        val element = file.findElementAt(offset) ?: return emptyList()
        val prop = findConfigProperty(element) ?: return emptyList()
        return listOf(PlayConfigDocumentationTarget(prop, PlayConfigService.getInstance(file.project)))
    }

    private fun findConfigProperty(element: PsiElement): PlayConfigProperty? {
        val prop = element.parent as? PlayConfigProperty
        if (prop != null) return prop
        val literal = element.parent as? PsiLiteralExpression ?: return null
        if (!PlayConfigContextDetector.isConfigKeyContext(literal)) return null
        return literal.reference?.resolve() as? PlayConfigProperty
    }
}

private class PlayConfigDocumentationTarget(
    private val prop: PlayConfigProperty,
    private val svc: PlayConfigService
) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val smartPtr = SmartPointerManager.createPointer(prop)
        return Pointer {
            val restored = smartPtr.element ?: return@Pointer null
            PlayConfigDocumentationTarget(restored, svc)
        }
    }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(prop.logicalKey).presentation()

    override fun computeDocumentation(): DocumentationResult =
        DocumentationResult.documentation(buildDocHtml())

    private fun buildDocHtml(): String = buildString {
        append(DocumentationMarkup.DEFINITION_START)
        append("<b>${prop.logicalKey}</b>")
        if (PlayConfigKnownKeys.isKnownKey(prop.logicalKey)) append(" <i>(known Play 1 key)</i>")
        append(DocumentationMarkup.DEFINITION_END)

        append(DocumentationMarkup.CONTENT_START)

        val resolution = svc.resolve(prop.logicalKey)
        val rows = mutableListOf<Pair<String, String>>()

        if (resolution.activeProfile != null) rows.add("Active profile" to resolution.activeProfile)

        rows.add("Effective value" to escapeHtml(resolution.effectiveValue ?: "—"))

        resolution.defaultValue?.let { rows.add("Default" to escapeHtml(it.value)) }
        resolution.profileValue?.let { rows.add("Profile (${it.profile})" to escapeHtml(it.value)) }

        val overrides = svc.keysForLogical(prop.logicalKey).mapNotNull { it.profile }
        if (overrides.isNotEmpty()) rows.add("Overrides" to overrides.joinToString(", "))

        if (resolution.unresolvedEnvironmentVariables.isNotEmpty()) {
            rows.add("Unresolved env vars" to resolution.unresolvedEnvironmentVariables.joinToString(", "))
        }

        val usageCount = countJavaUsages()
        val status = when {
            usageCount > 0 -> "used ($usageCount reference${if (usageCount > 1) "s" else ""})"
            !PlayConfigKnownKeys.isKnownKey(prop.logicalKey) -> "potentially unused"
            else -> "framework key"
        }
        rows.add("Status" to status)

        append("<table>")
        rows.forEach { (label, value) -> append("<tr><td><b>$label:</b></td><td>$value</td></tr>") }
        append("</table>")

        append(DocumentationMarkup.CONTENT_END)
    }

    private fun escapeHtml(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun countJavaUsages(): Int = try {
        com.intellij.psi.search.searches.ReferencesSearch.search(prop).findAll()
            .count { it.element is PsiLiteralExpression }
    } catch (e: Exception) {
        0
    }
}

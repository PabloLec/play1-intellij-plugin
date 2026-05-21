package com.github.pablolec.play1toolkit.playjobs.documentation

import com.github.pablolec.play1toolkit.playconfig.references.PlayConfigContextDetector
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobCategory
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInfo
import com.github.pablolec.play1toolkit.playjobs.service.PlayJobService
import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playmessages.service.PlayMessagesService
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil

class PlayJobDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (DumbService.isDumb(file.project)) return emptyList()
        val element = file.findElementAt(offset) ?: return emptyList()
        val psiClass = element.parent as? PsiClass
            ?: PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
            ?: return emptyList()

        val service = PlayJobService.getInstance(file.project)
        val info = service.findJobForClass(psiClass) ?: return emptyList()
        val invocations = runCatching { service.findInvocations(info) }.getOrDefault(emptyList()).size
        val enrichment = collectEnrichment(file.project, psiClass)
        val html = buildJobDoc(info, invocations, enrichment)

        return listOf(
            PlayJobDocTarget(
                SmartPointerManager.createPointer(psiClass),
                html
            )
        )
    }

    private fun buildJobDoc(info: PlayJobInfo, invocations: Int, enrichment: Enrichment): String {
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>Play job</b>")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<table>")
            append("<tr><td><b>Class:</b></td><td>${escape(info.qualifiedName ?: info.className)}</td></tr>")
            append("<tr><td><b>Category:</b></td><td>${escape(categoryLabel(info.category))}</td></tr>")
            append("<tr><td><b>Confidence:</b></td><td>${escape(info.confidence.name.lowercase())}</td></tr>")
            if (info.triggers.isNotEmpty()) {
                val triggers = info.triggers.joinToString(", ") { trigger ->
                    val simple = PlayJobUtils.triggerSimpleName(trigger.kind)
                    val raw = trigger.rawValue
                    val base = if (raw.isNullOrBlank()) "@$simple" else "@$simple(\"$raw\")"
                    if (trigger.async) "$base (async)" else base
                }
                append("<tr><td><b>Trigger:</b></td><td>${escape(triggers)}</td></tr>")
            }
            if (info.executionMethods.isNotEmpty()) {
                val methods = info.executionMethods.joinToString(", ") { "${it.name}()" }
                append("<tr><td><b>Execution:</b></td><td>${escape(methods)}</td></tr>")
            }
            append("<tr><td><b>Manual invocations:</b></td><td>$invocations</td></tr>")
            if (enrichment.configKeys.isNotEmpty()) {
                append("<tr><td><b>Configuration keys:</b></td><td>${escape(enrichment.configKeys.joinToString(", "))}</td></tr>")
            }
            if (enrichment.jpaModels.isNotEmpty()) {
                append("<tr><td><b>Models used:</b></td><td>${escape(enrichment.jpaModels.joinToString(", "))}</td></tr>")
            }
            if (enrichment.messageKeys.isNotEmpty()) {
                append("<tr><td><b>Message keys:</b></td><td>${escape(enrichment.messageKeys.joinToString(", "))}</td></tr>")
            }
            append("</table>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun collectEnrichment(project: Project, psiClass: PsiClass): Enrichment {
        val configService = runCatching { PlayConfigService.getInstance(project) }.getOrNull()
        val messagesService = runCatching { PlayMessagesService.getInstance(project) }.getOrNull()
        val jpaService = runCatching { PlayJpaModelService.getInstance(project) }.getOrNull()
        val allMessageKeys = runCatching { messagesService?.allKeys()?.toSet() }.getOrNull() ?: emptySet()
        val allJpaModelNames = runCatching { jpaService?.getAllModels()?.map { it.className }?.toSet() }.getOrNull() ?: emptySet()

        val configKeys = sortedSetOf<String>()
        val jpaModels = sortedSetOf<String>()
        val messageKeys = sortedSetOf<String>()

        PsiTreeUtil.findChildrenOfType(psiClass, PsiLiteralExpression::class.java).forEach { literal ->
            val value = literal.value as? String ?: return@forEach
            if (configService != null && PlayConfigContextDetector.isConfigKeyContext(literal)) {
                configKeys += value
            }
            if (value in allMessageKeys && isMessagesGetCall(literal)) {
                messageKeys += value
            }
        }
        PsiTreeUtil.findChildrenOfType(psiClass, PsiReferenceExpression::class.java).forEach { ref ->
            val name = ref.referenceName ?: return@forEach
            if (name in allJpaModelNames) {
                jpaModels += name
            }
        }
        return Enrichment(
            configKeys = configKeys.toList(),
            jpaModels = jpaModels.toList(),
            messageKeys = messageKeys.toList()
        )
    }

    private fun isMessagesGetCall(literal: PsiLiteralExpression): Boolean {
        val arg = literal.parent as? PsiExpressionList ?: return false
        val call = arg.parent as? PsiMethodCallExpression ?: return false
        val methodName = call.methodExpression.referenceName ?: return false
        if (methodName != "get" && methodName != "getMessage") return false
        val qualifier = call.methodExpression.qualifierExpression?.text ?: return false
        return qualifier == "Messages" || qualifier.endsWith(".Messages")
    }

    private fun categoryLabel(category: PlayJobCategory): String = when (category) {
        PlayJobCategory.STARTUP -> "Startup job"
        PlayJobCategory.SHUTDOWN -> "Shutdown job"
        PlayJobCategory.SCHEDULED_EVERY -> "Scheduled job"
        PlayJobCategory.SCHEDULED_CRON -> "Cron job"
        PlayJobCategory.MANUAL_ASYNC -> "Manual / async job"
        PlayJobCategory.UNKNOWN -> "Unknown scheduling"
    }

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private data class Enrichment(
        val configKeys: List<String>,
        val jpaModels: List<String>,
        val messageKeys: List<String>
    )
}

private class PlayJobDocTarget(
    private val pointer: SmartPsiElementPointer<PsiClass>,
    private val html: String
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> =
        Pointer { PlayJobDocTarget(pointer, html) }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(pointer.element?.name ?: "Play job").presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(html)

    override fun computeDocumentationHint(): String? = null

    @Suppress("unused")
    private fun ignored(): PsiElement? = pointer.element
}

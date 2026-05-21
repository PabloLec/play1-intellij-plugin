package com.github.pablolec.play1toolkit.playjobs.lineMarker

import com.github.pablolec.play1toolkit.playjobs.model.PlayJobCategory
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInfo
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobTrigger
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobTriggerKind
import com.github.pablolec.play1toolkit.playjobs.service.PlayJobService
import com.github.pablolec.play1toolkit.playjobs.util.PlayJobUtils
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

class PlayJobLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return
        val project = elements.first().project
        if (DumbService.isDumb(project)) return
        val service = PlayJobService.getInstance(project)

        for (element in elements) {
            when (element) {
                is PsiIdentifier -> handleIdentifier(element, service, result)
                is PsiJavaCodeReferenceElement -> handleAnnotationReference(element, service, result)
            }
        }
    }

    private fun handleIdentifier(
        identifier: PsiIdentifier,
        service: PlayJobService,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        when (val parent = identifier.parent) {
            is PsiClass -> {
                val info = service.findJobForClass(parent) ?: return
                result.add(createClassMarker(identifier, info))
            }
            is PsiMethod -> {
                val info = service.findJobForClass(parent.containingClass ?: return) ?: return
                val execMethod = info.executionMethods.firstOrNull { it.psiMethod == parent } ?: return
                result.add(createMethodMarker(identifier, info, execMethod.name))
            }
        }
    }

    private fun handleAnnotationReference(
        ref: PsiJavaCodeReferenceElement,
        service: PlayJobService,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        val annotation = ref.parent as? PsiAnnotation ?: return
        if (annotation.nameReferenceElement !== ref) return
        val simpleName = ref.referenceName ?: return
        val triggerKind = TRIGGER_BY_SIMPLE_NAME[simpleName] ?: return
        val ownerClass = PsiTreeUtil.getParentOfType(annotation, PsiClass::class.java) ?: return
        val info = service.findJobForClass(ownerClass) ?: return
        val trigger = info.triggers.firstOrNull { it.psiAnnotation == annotation && it.kind == triggerKind } ?: return
        val nameElement = ref.referenceNameElement ?: ref
        result.add(createAnnotationMarker(nameElement, info, trigger))
    }

    private fun createClassMarker(identifier: PsiIdentifier, info: PlayJobInfo): LineMarkerInfo<PsiElement> {
        val tooltip = buildClassTooltip(info)
        return LineMarkerInfo(
            identifier,
            identifier.textRange,
            iconFor(info.category),
            { tooltip },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { "Play job" }
        )
    }

    private fun createMethodMarker(
        identifier: PsiIdentifier,
        info: PlayJobInfo,
        methodName: String
    ): LineMarkerInfo<PsiElement> {
        val tooltip = "Play job execution method ($methodName) for ${info.className}"
        return LineMarkerInfo(
            identifier,
            identifier.textRange,
            AllIcons.Nodes.Method,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { tooltip }
        )
    }

    private fun createAnnotationMarker(
        anchor: PsiElement,
        info: PlayJobInfo,
        trigger: PlayJobTrigger
    ): LineMarkerInfo<PsiElement> {
        val tooltip = annotationTooltip(info, trigger)
        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            iconFor(info.category),
            { tooltip },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { tooltip }
        )
    }

    private fun buildClassTooltip(info: PlayJobInfo): String {
        val triggerSummary = info.triggers.joinToString(", ") { trigger ->
            val raw = trigger.rawValue
            val simple = PlayJobUtils.triggerSimpleName(trigger.kind)
            if (raw.isNullOrBlank()) "@$simple" else "@$simple(\"$raw\")"
        }
        val executionSummary = info.executionMethods.joinToString(", ") { "${it.name}()" }
        return buildString {
            append("Play ")
            append(categoryLabel(info.category))
            append(" job")
            if (triggerSummary.isNotBlank()) {
                append("\nTrigger: ")
                append(triggerSummary)
            }
            if (executionSummary.isNotBlank()) {
                append("\nExecution: ")
                append(executionSummary)
            }
        }
    }

    private fun annotationTooltip(info: PlayJobInfo, trigger: PlayJobTrigger): String {
        val raw = trigger.rawValue
        return when (trigger.kind) {
            PlayJobTriggerKind.ON_APPLICATION_START ->
                "Runs when the Play application starts" + if (trigger.async) " (async)" else ""
            PlayJobTriggerKind.ON_APPLICATION_STOP -> "Runs when the Play application stops"
            PlayJobTriggerKind.EVERY -> if (raw.isNullOrBlank()) "Play job schedule (every)" else "Play job schedule: every $raw"
            PlayJobTriggerKind.ON -> if (raw.isNullOrBlank()) "Play job cron schedule" else "Play job cron schedule: $raw"
        } + "\nClass: ${info.className}"
    }

    private fun categoryLabel(category: PlayJobCategory): String = when (category) {
        PlayJobCategory.STARTUP -> "startup"
        PlayJobCategory.SHUTDOWN -> "shutdown"
        PlayJobCategory.SCHEDULED_EVERY -> "scheduled"
        PlayJobCategory.SCHEDULED_CRON -> "cron"
        PlayJobCategory.MANUAL_ASYNC -> "manual / async"
        PlayJobCategory.UNKNOWN -> "unknown-scheduling"
    }

    private fun iconFor(category: PlayJobCategory) = when (category) {
        PlayJobCategory.STARTUP -> AllIcons.Actions.Execute
        PlayJobCategory.SHUTDOWN -> AllIcons.Actions.Cancel
        PlayJobCategory.SCHEDULED_EVERY -> AllIcons.Vcs.History
        PlayJobCategory.SCHEDULED_CRON -> AllIcons.Vcs.History
        PlayJobCategory.MANUAL_ASYNC -> AllIcons.Actions.RunAll
        PlayJobCategory.UNKNOWN -> AllIcons.General.Information
    }

    companion object {
        private val TRIGGER_BY_SIMPLE_NAME = mapOf(
            "OnApplicationStart" to PlayJobTriggerKind.ON_APPLICATION_START,
            "OnApplicationStop" to PlayJobTriggerKind.ON_APPLICATION_STOP,
            "Every" to PlayJobTriggerKind.EVERY,
            "On" to PlayJobTriggerKind.ON
        )
    }
}

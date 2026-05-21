package com.github.pablolec.play1toolkit.playjobs.util

import com.github.pablolec.play1toolkit.playjobs.model.PlayJobCategory
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobConfidence
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobExecutionMethod
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobInfo
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobTrigger
import com.github.pablolec.play1toolkit.playjobs.model.PlayJobTriggerKind
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier

private const val PLAY_JOB_FQN = "play.jobs.Job"

private val TRIGGER_BY_SIMPLE_NAME: Map<String, PlayJobTriggerKind> = mapOf(
    "OnApplicationStart" to PlayJobTriggerKind.ON_APPLICATION_START,
    "OnApplicationStop" to PlayJobTriggerKind.ON_APPLICATION_STOP,
    "Every" to PlayJobTriggerKind.EVERY,
    "On" to PlayJobTriggerKind.ON
)

private val EXECUTION_METHOD_NAMES = setOf("doJob", "doJobWithResult")

private val POSITIONAL_NAME_SUFFIXES = listOf("Job", "Batch", "Task", "Scheduler")

private val EVERY_VALUE_REGEX = Regex("""^\d+(s|mn|h|d)$""")

object PlayJobUtils {

    fun isCandidateClass(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) return false
        if (psiClass.qualifiedName == PLAY_JOB_FQN) return false
        return extendsPlayJob(psiClass) ||
            findTriggers(psiClass).isNotEmpty() ||
            (isUnderAppJobs(psiClass) && hasPositionalName(psiClass.name))
    }

    fun extendsPlayJob(psiClass: PsiClass): Boolean {
        if (hasDirectPlayJobSuperTypeName(psiClass)) return true
        var superClass = psiClass.superClass
        var depth = 0
        while (superClass != null && depth < 5) {
            if (superClass.qualifiedName == PLAY_JOB_FQN) return true
            superClass = superClass.superClass
            depth++
        }
        return false
    }

    fun findTriggers(psiClass: PsiClass): List<PlayJobTrigger> {
        val modifierList = psiClass.modifierList ?: return emptyList()
        return modifierList.annotations.mapNotNull { annotation ->
            val simpleName = annotationSimpleName(annotation) ?: return@mapNotNull null
            val kind = TRIGGER_BY_SIMPLE_NAME[simpleName] ?: return@mapNotNull null
            PlayJobTrigger(
                kind = kind,
                rawValue = readStringAttribute(annotation, "value"),
                async = readBooleanAttribute(annotation, "async") ?: false,
                psiAnnotation = annotation
            )
        }
    }

    fun findExecutionMethods(psiClass: PsiClass): List<PlayJobExecutionMethod> {
        return psiClass.methods
            .filter { it.name in EXECUTION_METHOD_NAMES && !it.hasModifierProperty(PsiModifier.STATIC) }
            .map { method ->
                val returnsResult = method.name == "doJobWithResult" ||
                    method.returnType?.canonicalText?.let { it != "void" } == true
                PlayJobExecutionMethod(method.name, method, returnsResult)
            }
    }

    fun isUnderAppJobs(psiClass: PsiClass): Boolean {
        val vf = psiClass.containingFile?.virtualFile ?: return false
        return vf.path.contains("/app/jobs/")
    }

    fun hasPositionalName(name: String?): Boolean {
        if (name == null) return false
        return POSITIONAL_NAME_SUFFIXES.any { name.endsWith(it) }
    }

    fun classify(psiClass: PsiClass): PlayJobInfo? {
        if (!isCandidateClass(psiClass)) return null
        val triggers = findTriggers(psiClass)
        val executionMethods = findExecutionMethods(psiClass)
        val extendsJob = extendsPlayJob(psiClass)
        val reasons = mutableListOf<String>()

        if (extendsJob) reasons += "extends play.jobs.Job"
        triggers.forEach { trigger ->
            val raw = trigger.rawValue
            val rendered = if (raw.isNullOrBlank()) "@${triggerSimpleName(trigger.kind)}"
                else "@${triggerSimpleName(trigger.kind)}(\"$raw\")"
            reasons += "annotated with $rendered"
        }
        if (executionMethods.isNotEmpty()) {
            reasons += "declares ${executionMethods.joinToString(", ") { "${it.name}()" }}"
        }
        if (!extendsJob && triggers.isEmpty() && isUnderAppJobs(psiClass)) {
            reasons += "located under app/jobs"
        }

        val confidence = when {
            extendsJob && (triggers.isNotEmpty() || executionMethods.isNotEmpty()) -> PlayJobConfidence.HIGH
            extendsJob || triggers.isNotEmpty() -> PlayJobConfidence.MEDIUM
            else -> PlayJobConfidence.LOW
        }

        val category = categoryFor(triggers, extendsJob, confidence)

        return PlayJobInfo(
            className = psiClass.name ?: "",
            qualifiedName = psiClass.qualifiedName,
            psiClass = psiClass,
            extendsPlayJob = extendsJob,
            triggers = triggers,
            executionMethods = executionMethods,
            category = category,
            confidence = confidence,
            reasons = reasons
        )
    }

    fun parseEveryValueIsValid(value: String?): Boolean {
        if (value == null) return false
        return EVERY_VALUE_REGEX.matches(value)
    }

    fun triggerSimpleName(kind: PlayJobTriggerKind): String = when (kind) {
        PlayJobTriggerKind.ON_APPLICATION_START -> "OnApplicationStart"
        PlayJobTriggerKind.ON_APPLICATION_STOP -> "OnApplicationStop"
        PlayJobTriggerKind.EVERY -> "Every"
        PlayJobTriggerKind.ON -> "On"
    }

    private fun categoryFor(
        triggers: List<PlayJobTrigger>,
        extendsJob: Boolean,
        confidence: PlayJobConfidence
    ): PlayJobCategory {
        val kinds = triggers.map { it.kind }.toSet()
        return when {
            PlayJobTriggerKind.ON_APPLICATION_START in kinds -> PlayJobCategory.STARTUP
            PlayJobTriggerKind.ON_APPLICATION_STOP in kinds -> PlayJobCategory.SHUTDOWN
            PlayJobTriggerKind.ON in kinds -> PlayJobCategory.SCHEDULED_CRON
            PlayJobTriggerKind.EVERY in kinds -> PlayJobCategory.SCHEDULED_EVERY
            extendsJob -> PlayJobCategory.MANUAL_ASYNC
            confidence == PlayJobConfidence.LOW -> PlayJobCategory.UNKNOWN
            else -> PlayJobCategory.UNKNOWN
        }
    }

    private fun hasDirectPlayJobSuperTypeName(psiClass: PsiClass): Boolean {
        return psiClass.extendsListTypes.any { type ->
            val canonicalText = type.canonicalText
            canonicalText == PLAY_JOB_FQN ||
                canonicalText.endsWith(".Job") ||
                canonicalText == "Job"
        }
    }

    private fun annotationSimpleName(annotation: PsiAnnotation): String? {
        val qualified = annotation.qualifiedName
        if (qualified != null) return qualified.substringAfterLast('.')
        val text = annotation.text ?: return null
        return text.substringAfterLast('.').removePrefix("@").substringBefore('(').trim().takeIf { it.isNotEmpty() }
    }

    private fun readStringAttribute(annotation: PsiAnnotation, name: String): String? {
        val value = annotation.findAttributeValue(name) as? PsiLiteralExpression ?: return null
        return value.value as? String
    }

    private fun readBooleanAttribute(annotation: PsiAnnotation, name: String): Boolean? {
        val value = annotation.findAttributeValue(name) as? PsiLiteralExpression ?: return null
        return value.value as? Boolean
    }
}

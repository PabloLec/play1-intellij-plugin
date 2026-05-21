package com.github.pablolec.play1toolkit.playjobs.model

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNewExpression

enum class PlayJobCategory {
    STARTUP,
    SCHEDULED_EVERY,
    SCHEDULED_CRON,
    SHUTDOWN,
    MANUAL_ASYNC,
    UNKNOWN
}

enum class PlayJobTriggerKind {
    ON_APPLICATION_START,
    ON_APPLICATION_STOP,
    EVERY,
    ON
}

enum class PlayJobInvocationKind {
    NOW,
    IN,
    AT,
    AFTER_REQUEST,
    NEW_ONLY
}

enum class PlayJobConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class PlayJobTrigger(
    val kind: PlayJobTriggerKind,
    val rawValue: String?,
    val async: Boolean,
    val psiAnnotation: PsiAnnotation
)

data class PlayJobExecutionMethod(
    val name: String,
    val psiMethod: PsiMethod,
    val returnsResult: Boolean
)

data class PlayJobInvocation(
    val psiNewExpression: PsiNewExpression,
    val callChain: String,
    val kind: PlayJobInvocationKind
)

data class PlayJobInfo(
    val className: String,
    val qualifiedName: String?,
    val psiClass: PsiClass,
    val extendsPlayJob: Boolean,
    val triggers: List<PlayJobTrigger>,
    val executionMethods: List<PlayJobExecutionMethod>,
    val category: PlayJobCategory,
    val confidence: PlayJobConfidence,
    val reasons: List<String>
)

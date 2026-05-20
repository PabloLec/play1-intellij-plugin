package com.github.pablolec.play1toolkit.response

import com.intellij.psi.PsiElement

data class PlayResponseOutcome(
    val kind: PlayResponseKind,
    val sourceElement: PsiElement,
    val details: String? = null,
    val callText: String? = null,
    val statusCode: Int? = null,
    val confidence: PlayResponseConfidence = PlayResponseConfidence.HIGH,
)

data class PlayEndpointResponseInfo(
    val kind: PlayResponseKind,
    val outcomes: List<PlayResponseOutcome>,
    val confidence: PlayResponseConfidence,
) {
    val primaryOutcomes: List<PlayResponseOutcome>
        get() = outcomes.filter { it.kind in PRIMARY_KINDS }

    val statusOutcomes: List<PlayResponseOutcome>
        get() = outcomes.filter { it.kind == PlayResponseKind.STATUS || it.kind == PlayResponseKind.ERROR }

    companion object {
        val PRIMARY_KINDS = setOf(
            PlayResponseKind.HTML,
            PlayResponseKind.JSON,
            PlayResponseKind.XML,
            PlayResponseKind.TEXT,
            PlayResponseKind.BINARY,
            PlayResponseKind.REDIRECT,
        )
    }
}

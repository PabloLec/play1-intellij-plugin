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
)

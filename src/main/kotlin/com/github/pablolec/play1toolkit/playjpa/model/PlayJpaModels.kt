package com.github.pablolec.play1toolkit.playjpa.model

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField

data class PlayJpaModelInfo(
    val className: String,
    val qualifiedName: String?,
    val psiClass: PsiClass,
    val sourceKind: PlayJpaModelSourceKind,
    val idField: PlayJpaFieldInfo?,
    val fields: List<PlayJpaFieldInfo>,
    val relations: List<PlayJpaRelationInfo>
)

enum class PlayJpaModelSourceKind {
    EXTENDS_MODEL,
    EXTENDS_GENERIC_MODEL,
    JPA_ENTITY,
    MAPPED_SUPERCLASS,
    EMBEDDABLE,
    APP_MODELS_CONVENTION,
    MIXED
}

data class PlayJpaFieldInfo(
    val name: String,
    val typeText: String,
    val psiField: PsiField,
    val annotations: List<String>
)

data class PlayJpaRelationInfo(
    val fieldName: String,
    val targetModel: String?,
    val relationKind: PlayJpaRelationKind,
    val psiField: PsiField
)

enum class PlayJpaRelationKind {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY,
    UNKNOWN
}

enum class PlayAppModelCategory {
    PERSISTENT_PLAY_MODEL,
    PERSISTENT_GENERIC_MODEL,
    JPA_ENTITY,
    MAPPED_SUPERCLASS,
    EMBEDDABLE,
    DTO_OR_VIEW_MODEL,
    BUSINESS_OBJECT,
    SERVICE_OR_HELPER,
    ENUM,
    UNCLASSIFIED
}

enum class PlayAppModelConfidence {
    HIGH,
    MEDIUM,
    LOW
}

data class PlayAppModelClassification(
    val category: PlayAppModelCategory,
    val confidence: PlayAppModelConfidence,
    val reasons: List<String>
)

data class PlayAppModelEntry(
    val className: String,
    val qualifiedName: String?,
    val psiClass: PsiClass,
    val classification: PlayAppModelClassification,
    val fieldCount: Int,
    val methodCount: Int,
    val enumConstantCount: Int,
    val persistentModel: PlayJpaModelInfo?
)

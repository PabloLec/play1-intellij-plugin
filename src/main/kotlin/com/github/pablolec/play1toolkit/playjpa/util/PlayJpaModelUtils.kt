package com.github.pablolec.play1toolkit.playjpa.util

import com.github.pablolec.play1toolkit.playjpa.model.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier

private val RELATION_ANNOTATIONS = setOf("OneToOne", "OneToMany", "ManyToOne", "ManyToMany")
private val MODEL_BASE_CLASSES = setOf("play.db.jpa.Model", "play.db.jpa.GenericModel", "play.db.jpa.JPABase")
private val ID_ANNOTATIONS = setOf("Id", "EmbeddedId")
private val SKIP_ANNOTATIONS = setOf("Transient")
private val RELATION_KINDS = mapOf(
    "OneToOne" to PlayJpaRelationKind.ONE_TO_ONE,
    "OneToMany" to PlayJpaRelationKind.ONE_TO_MANY,
    "ManyToOne" to PlayJpaRelationKind.MANY_TO_ONE,
    "ManyToMany" to PlayJpaRelationKind.MANY_TO_MANY
)

object PlayJpaModelUtils {

    fun isPlayJpaModel(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) return false
        if (extendsPlayModel(psiClass)) return true
        if (hasEntityAnnotation(psiClass)) return true
        if (isUnderAppModels(psiClass)) return true
        return false
    }

    fun extendsPlayModel(psiClass: PsiClass): Boolean {
        var superClass = psiClass.superClass
        var depth = 0
        while (superClass != null && depth < 5) {
            val fqn = superClass.qualifiedName ?: break
            if (fqn in MODEL_BASE_CLASSES) return true
            superClass = superClass.superClass
            depth++
        }
        return false
    }

    fun hasEntityAnnotation(psiClass: PsiClass): Boolean =
        psiClass.modifierList?.annotations?.any { it.qualifiedName?.endsWith("Entity") == true } == true

    fun isUnderAppModels(psiClass: PsiClass): Boolean {
        val vf = psiClass.containingFile?.virtualFile ?: return false
        return vf.path.contains("/app/models/")
    }

    fun isRelationAnnotation(name: String): Boolean = name in RELATION_ANNOTATIONS

    fun getJpaAnnotations(field: PsiField): List<String> =
        field.modifierList?.annotations?.mapNotNull { ann ->
            ann.qualifiedName?.substringAfterLast('.')
        } ?: emptyList()

    fun buildModelInfo(psiClass: PsiClass): PlayJpaModelInfo {
        val allAnnotations = psiClass.modifierList?.annotations?.mapNotNull {
            it.qualifiedName?.substringAfterLast('.')
        } ?: emptyList()
        val sourceKind = determineSourceKind(psiClass, allAnnotations)

        val fields = mutableListOf<PlayJpaFieldInfo>()
        val relations = mutableListOf<PlayJpaRelationInfo>()
        var idField: PlayJpaFieldInfo? = null

        for (field in psiClass.allFields) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue
            val annNames = getJpaAnnotations(field)
            if (SKIP_ANNOTATIONS.any { it in annNames }) continue

            val fieldInfo = PlayJpaFieldInfo(
                name = field.name,
                typeText = field.type.presentableText,
                psiField = field,
                annotations = annNames
            )

            if (ID_ANNOTATIONS.any { it in annNames }) {
                idField = fieldInfo
            }

            val relationAnn = annNames.firstOrNull { it in RELATION_ANNOTATIONS }
            if (relationAnn != null) {
                val targetType = extractRelationTargetType(field)
                relations.add(PlayJpaRelationInfo(
                    fieldName = field.name,
                    targetModel = targetType,
                    relationKind = RELATION_KINDS[relationAnn] ?: PlayJpaRelationKind.UNKNOWN,
                    psiField = field
                ))
            } else {
                fields.add(fieldInfo)
            }
        }

        return PlayJpaModelInfo(
            className = psiClass.name ?: "",
            qualifiedName = psiClass.qualifiedName,
            psiClass = psiClass,
            sourceKind = sourceKind,
            idField = idField,
            fields = fields,
            relations = relations
        )
    }

    private fun determineSourceKind(psiClass: PsiClass, annotations: List<String>): PlayJpaModelSourceKind {
        val extendsModel = extendsPlayModel(psiClass)
        val hasEntity = "Entity" in annotations
        val inModels = isUnderAppModels(psiClass)

        val superFqn = psiClass.superClass?.qualifiedName
        val extendsGeneric = superFqn == "play.db.jpa.GenericModel"

        return when {
            extendsModel && hasEntity -> PlayJpaModelSourceKind.MIXED
            extendsGeneric -> PlayJpaModelSourceKind.EXTENDS_GENERIC_MODEL
            extendsModel -> PlayJpaModelSourceKind.EXTENDS_MODEL
            hasEntity -> PlayJpaModelSourceKind.JPA_ENTITY
            inModels -> PlayJpaModelSourceKind.APP_MODELS_CONVENTION
            else -> PlayJpaModelSourceKind.APP_MODELS_CONVENTION
        }
    }

    private fun extractRelationTargetType(field: PsiField): String? {
        val typeText = field.type.presentableText
        // e.g. "List<User>" → "User", "Set<Tag>" → "Tag", "User" → "User"
        val generic = Regex("""<(\w+)>""").find(typeText)?.groupValues?.get(1)
        return generic ?: typeText.trim()
    }
}

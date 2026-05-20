package com.github.pablolec.play1toolkit.playjpa.util

import com.github.pablolec.play1toolkit.playjpa.model.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier

private val RELATION_ANNOTATIONS = setOf("OneToOne", "OneToMany", "ManyToOne", "ManyToMany")
private val MODEL_BASE_CLASSES = setOf("play.db.jpa.Model", "play.db.jpa.GenericModel", "play.db.jpa.JPABase")
private val ID_ANNOTATIONS = setOf("Id", "EmbeddedId")
private val SKIP_ANNOTATIONS = setOf("Transient")
private val JPA_FIELD_ANNOTATIONS = setOf(
    "Id",
    "EmbeddedId",
    "Column",
    "JoinColumn",
    "JoinTable",
    "Enumerated",
    "Temporal",
    "Lob",
    "Version",
    "Basic",
    "Transient",
    "OneToOne",
    "OneToMany",
    "ManyToOne",
    "ManyToMany"
)
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
        if (isUnderAppModels(psiClass) && hasJpaLikeStructure(psiClass)) return true
        return false
    }

    fun extendsPlayModel(psiClass: PsiClass): Boolean {
        if (hasPlayModelSuperTypeName(psiClass)) return true
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
        psiClass.modifierList?.annotations?.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName?.endsWith("Entity") == true || annotation.text.substringAfterLast('.').removePrefix("@") == "Entity"
        } == true

    fun isUnderAppModels(psiClass: PsiClass): Boolean {
        val vf = psiClass.containingFile?.virtualFile ?: return false
        return vf.path.contains("/app/models/")
    }

    fun isRelationAnnotation(name: String): Boolean = name in RELATION_ANNOTATIONS

    fun getJpaAnnotations(field: PsiField): List<String> =
        field.modifierList?.annotations?.mapNotNull { ann ->
            ann.qualifiedName?.substringAfterLast('.') ?: ann.text.substringAfterLast('.').removePrefix("@")
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
        val conventionModel = isUnderAppModels(psiClass) && hasJpaLikeStructure(psiClass)

        val superFqn = psiClass.superClass?.qualifiedName
        val extendsGeneric = superFqn == "play.db.jpa.GenericModel"

        return when {
            extendsModel && hasEntity -> PlayJpaModelSourceKind.MIXED
            extendsGeneric -> PlayJpaModelSourceKind.EXTENDS_GENERIC_MODEL
            extendsModel -> PlayJpaModelSourceKind.EXTENDS_MODEL
            hasEntity -> PlayJpaModelSourceKind.JPA_ENTITY
            conventionModel -> PlayJpaModelSourceKind.APP_MODELS_CONVENTION
            else -> PlayJpaModelSourceKind.APP_MODELS_CONVENTION
        }
    }

    private fun hasPlayModelSuperTypeName(psiClass: PsiClass): Boolean {
        return psiClass.extendsListTypes.any { type ->
            val canonicalText = type.canonicalText
            canonicalText in MODEL_BASE_CLASSES ||
                canonicalText.endsWith(".Model") ||
                canonicalText.endsWith(".GenericModel") ||
                canonicalText.endsWith(".JPABase") ||
                canonicalText == "Model" ||
                canonicalText == "GenericModel" ||
                canonicalText == "JPABase"
        }
    }

    private fun hasJpaLikeStructure(psiClass: PsiClass): Boolean {
        val ownFields = psiClass.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }
        if (ownFields.isEmpty()) return false

        val hasJpaAnnotations = ownFields.any { field ->
            getJpaAnnotations(field).any { it in JPA_FIELD_ANNOTATIONS }
        }
        if (hasJpaAnnotations) return true

        val hasDeclaredIdField = ownFields.any { field ->
            field.name == "id" && field.type.presentableText in setOf("Long", "long", "Integer", "int", "String")
        }
        if (hasDeclaredIdField) return true

        val hasEntityLikeName = psiClass.name?.let { name ->
            name.endsWith("Model") || name.endsWith("Entity")
        } == true
        return hasEntityLikeName && ownFields.any { field -> field.name != "serialVersionUID" }
    }

    private fun extractRelationTargetType(field: PsiField): String? {
        val typeText = field.type.presentableText
        // e.g. "List<User>" → "User", "Set<Tag>" → "Tag", "User" → "User"
        val generic = Regex("""<(\w+)>""").find(typeText)?.groupValues?.get(1)
        return generic ?: typeText.trim()
    }
}

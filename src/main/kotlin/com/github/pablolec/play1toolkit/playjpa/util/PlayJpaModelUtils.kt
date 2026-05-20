package com.github.pablolec.play1toolkit.playjpa.util

import com.github.pablolec.play1toolkit.playjpa.model.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
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
private val NON_MODEL_NAME_SUFFIXES = listOf(
    "DTO",
    "Dto",
    "Request",
    "Response",
    "Form",
    "Criteria",
    "Filter",
    "Summary",
    "ViewModel",
    "Srv",
    "Service",
    "ServiceImpl",
    "Builder",
    "Provider",
    "Factory",
    "Mock",
    "Converter",
    "Mapper"
)
private val DTO_NAME_SUFFIXES = listOf("DTO", "Dto", "Request", "Response", "Form", "Criteria", "Filter", "Summary", "ViewModel")
private val SERVICE_NAME_SUFFIXES = listOf("Srv", "Service", "ServiceImpl", "Manager", "Helper", "Utils", "Util", "Factory", "Builder", "Repository", "Dao", "DAO", "Provider", "Converter", "Mapper")
private val RELATION_KINDS = mapOf(
    "OneToOne" to PlayJpaRelationKind.ONE_TO_ONE,
    "OneToMany" to PlayJpaRelationKind.ONE_TO_MANY,
    "ManyToOne" to PlayJpaRelationKind.MANY_TO_ONE,
    "ManyToMany" to PlayJpaRelationKind.MANY_TO_MANY
)

object PlayJpaModelUtils {

    fun isPlayJpaModel(psiClass: PsiClass): Boolean {
        if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) return false
        if (hasMappedSuperclassAnnotation(psiClass) || hasEmbeddableAnnotation(psiClass)) return false
        if (psiClass.name?.let(::hasNonModelNameSuffix) == true) return false

        val directPlayModel = hasDirectPlayModelSuperTypeName(psiClass)
        val inheritedPlayModel = extendsPlayModel(psiClass)
        val directJpaSignals = hasDirectPersistenceSignal(psiClass)
        val entity = hasEntityAnnotation(psiClass)

        if (directPlayModel) return true
        if (entity && (inheritedPlayModel || isUnderAppModels(psiClass) || directJpaSignals)) return true
        if (directJpaSignals && (inheritedPlayModel || isUnderAppModels(psiClass))) return true
        return false
    }

    fun extendsPlayModel(psiClass: PsiClass): Boolean {
        if (hasDirectPlayModelSuperTypeName(psiClass)) return true
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
        hasClassAnnotation(psiClass, "Entity")

    fun hasMappedSuperclassAnnotation(psiClass: PsiClass): Boolean =
        hasClassAnnotation(psiClass, "MappedSuperclass")

    fun hasEmbeddableAnnotation(psiClass: PsiClass): Boolean =
        hasClassAnnotation(psiClass, "Embeddable")

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
        val hasMappedSuperclass = "MappedSuperclass" in annotations
        val hasEmbeddable = "Embeddable" in annotations
        val conventionModel = isUnderAppModels(psiClass) && hasJpaLikeStructure(psiClass)

        val directSuperText = psiClass.extendsListTypes.firstOrNull()?.canonicalText
        val extendsGeneric = directSuperText == "play.db.jpa.GenericModel" || directSuperText == "GenericModel"

        return when {
            hasMappedSuperclass -> PlayJpaModelSourceKind.MAPPED_SUPERCLASS
            hasEmbeddable -> PlayJpaModelSourceKind.EMBEDDABLE
            extendsModel && hasEntity -> PlayJpaModelSourceKind.MIXED
            extendsGeneric -> PlayJpaModelSourceKind.EXTENDS_GENERIC_MODEL
            extendsModel -> PlayJpaModelSourceKind.EXTENDS_MODEL
            hasEntity -> PlayJpaModelSourceKind.JPA_ENTITY
            conventionModel -> PlayJpaModelSourceKind.APP_MODELS_CONVENTION
            else -> PlayJpaModelSourceKind.APP_MODELS_CONVENTION
        }
    }

    fun classifyAppModel(psiClass: PsiClass): PlayAppModelClassification? {
        if (!isUnderAppModels(psiClass)) return null
        if (psiClass.isInterface || psiClass.isAnnotationType) return null
        if (psiClass.isEnum) {
            return PlayAppModelClassification(
                PlayAppModelCategory.ENUM,
                PlayAppModelConfidence.HIGH,
                listOf("enum declared under app/models")
            )
        }

        val name = psiClass.name ?: return null
        val directPlayModel = hasDirectPlayModelSuperTypeName(psiClass)
        val inheritedPlayModel = extendsPlayModel(psiClass)
        val entity = hasEntityAnnotation(psiClass)
        val mappedSuperclass = hasMappedSuperclassAnnotation(psiClass)
        val embeddable = hasEmbeddableAnnotation(psiClass)
        val directJpaSignals = hasDirectPersistenceSignal(psiClass)
        val ownFields = ownInstanceFields(psiClass)
        val ownMethods = ownMethods(psiClass)

        if (mappedSuperclass) {
            return PlayAppModelClassification(
                PlayAppModelCategory.MAPPED_SUPERCLASS,
                PlayAppModelConfidence.HIGH,
                buildList {
                    add("annotated with @MappedSuperclass")
                    if (inheritedPlayModel) add("inherits from Play JPA base type")
                    if (directJpaSignals) add("declares direct JPA field annotations")
                }
            )
        }
        if (embeddable) {
            return PlayAppModelClassification(
                PlayAppModelCategory.EMBEDDABLE,
                PlayAppModelConfidence.HIGH,
                buildList {
                    add("annotated with @Embeddable")
                    if (directJpaSignals) add("declares direct JPA field annotations")
                }
            )
        }
        if (entity && directSuperTypeMatches(psiClass, "GenericModel")) {
            return PlayAppModelClassification(
                PlayAppModelCategory.PERSISTENT_GENERIC_MODEL,
                PlayAppModelConfidence.HIGH,
                buildList {
                    add("annotated with @Entity")
                    add("extends GenericModel")
                    if (hasIdLikeField(psiClass)) add("declares @Id or @EmbeddedId")
                    if (directJpaSignals) add("declares direct JPA field annotations")
                }
            )
        }
        if (entity && (directSuperTypeMatches(psiClass, "Model") || directSuperTypeMatches(psiClass, "JPABase"))) {
            return PlayAppModelClassification(
                PlayAppModelCategory.PERSISTENT_PLAY_MODEL,
                PlayAppModelConfidence.HIGH,
                buildList {
                    add("annotated with @Entity")
                    add("extends ${psiClass.extendsListTypes.firstOrNull()?.presentableText ?: "Play JPA base type"}")
                    if (hasIdLikeField(psiClass)) add("declares @Id or @EmbeddedId")
                    if (directJpaSignals) add("declares direct JPA field annotations")
                }
            )
        }
        if (directPlayModel) {
            return PlayAppModelClassification(
                if (directSuperTypeMatches(psiClass, "GenericModel")) PlayAppModelCategory.PERSISTENT_GENERIC_MODEL else PlayAppModelCategory.PERSISTENT_PLAY_MODEL,
                if (entity || directJpaSignals) PlayAppModelConfidence.HIGH else PlayAppModelConfidence.MEDIUM,
                buildList {
                    add("extends ${psiClass.extendsListTypes.firstOrNull()?.presentableText ?: "Play JPA base type"}")
                    if (entity) add("annotated with @Entity")
                    if (hasIdLikeField(psiClass)) add("declares @Id or @EmbeddedId")
                    if (directJpaSignals) add("declares direct JPA field annotations")
                }
            )
        }
        if (entity) {
            return PlayAppModelClassification(
                PlayAppModelCategory.JPA_ENTITY,
                PlayAppModelConfidence.HIGH,
                buildList {
                    add("annotated with @Entity")
                    if (directJpaSignals) add("declares direct JPA field annotations")
                    if (inheritedPlayModel) add("inherits from Play JPA base type")
                }
            )
        }
        if (directJpaSignals && inheritedPlayModel) {
            return PlayAppModelClassification(
                PlayAppModelCategory.JPA_ENTITY,
                PlayAppModelConfidence.MEDIUM,
                buildList {
                    add("inherits from Play JPA base type")
                    add("declares direct JPA field annotations")
                    add("located under app/models")
                }
            )
        }
        if (hasDtoLikeName(name)) {
            return PlayAppModelClassification(
                PlayAppModelCategory.DTO_OR_VIEW_MODEL,
                PlayAppModelConfidence.HIGH,
                buildList {
                    add("class name ends with a DTO/view-model suffix")
                    if (ownFields.isNotEmpty()) add("declares ${ownFields.size} instance fields")
                    if (psiClass.isRecord) add("declared as a Java record")
                }
            )
        }
        if (hasServiceLikeName(name)) {
            return PlayAppModelClassification(
                PlayAppModelCategory.SERVICE_OR_HELPER,
                if (ownMethods.size >= 3) PlayAppModelConfidence.HIGH else PlayAppModelConfidence.MEDIUM,
                buildList {
                    add("class name ends with a service/helper suffix")
                    if (ownMethods.isNotEmpty()) add("declares ${ownMethods.size} methods")
                    if (ownFields.isEmpty()) add("has little or no instance state")
                }
            )
        }

        val domainReasons = mutableListOf("located under app/models")
        if (ownFields.isNotEmpty()) domainReasons += "declares ${ownFields.size} instance fields"
        if (ownMethods.any { !it.hasModifierProperty(PsiModifier.STATIC) }) {
            domainReasons += "contains instance-level behavior"
        }
        if (ownFields.isNotEmpty()) {
            return PlayAppModelClassification(
                PlayAppModelCategory.BUSINESS_OBJECT,
                PlayAppModelConfidence.MEDIUM,
                domainReasons
            )
        }

        return PlayAppModelClassification(
            PlayAppModelCategory.UNCLASSIFIED,
            PlayAppModelConfidence.LOW,
            listOf("located under app/models", "no strong persistence, DTO, enum, or service/helper signal detected")
        )
    }

    private fun hasDirectPlayModelSuperTypeName(psiClass: PsiClass): Boolean {
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
        val ownFields = ownInstanceFields(psiClass)
        if (ownFields.isEmpty()) return false

        val hasJpaAnnotations = ownFields.any { field -> getJpaAnnotations(field).any { it in JPA_FIELD_ANNOTATIONS } }
        if (hasJpaAnnotations) return true

        val hasEntityLikeName = psiClass.name?.let { name ->
            name.endsWith("Model") || name.endsWith("Entity")
        } == true
        return hasEntityLikeName && ownFields.any { field -> field.name != "serialVersionUID" }
    }

    private fun hasNonModelNameSuffix(name: String): Boolean =
        NON_MODEL_NAME_SUFFIXES.any { name.endsWith(it) }

    private fun hasDtoLikeName(name: String): Boolean =
        DTO_NAME_SUFFIXES.any { name.endsWith(it) }

    private fun hasServiceLikeName(name: String): Boolean =
        SERVICE_NAME_SUFFIXES.any { name.endsWith(it) }

    private fun hasDirectPersistenceSignal(psiClass: PsiClass): Boolean {
        return ownInstanceFields(psiClass).any { field ->
            getJpaAnnotations(field).any { it in JPA_FIELD_ANNOTATIONS }
        }
    }

    private fun hasIdLikeField(psiClass: PsiClass): Boolean {
        return ownInstanceFields(psiClass).any { field ->
            getJpaAnnotations(field).any { it in ID_ANNOTATIONS }
        }
    }

    private fun hasClassAnnotation(psiClass: PsiClass, simpleName: String): Boolean =
        psiClass.modifierList?.annotations?.any { annotation ->
            val qualifiedName = annotation.qualifiedName
            qualifiedName?.endsWith(simpleName) == true || annotation.text.substringAfterLast('.').removePrefix("@") == simpleName
        } == true

    private fun directSuperTypeMatches(psiClass: PsiClass, simpleName: String): Boolean {
        return psiClass.extendsListTypes.any { type ->
            val canonicalText = type.canonicalText
            canonicalText == simpleName || canonicalText.endsWith(".$simpleName")
        }
    }

    private fun ownInstanceFields(psiClass: PsiClass): List<PsiField> =
        psiClass.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }

    private fun ownMethods(psiClass: PsiClass): List<PsiMethod> =
        psiClass.methods.filterNot { it.isConstructor || it.hasModifierProperty(PsiModifier.STATIC) }

    private fun extractRelationTargetType(field: PsiField): String? {
        val typeText = field.type.presentableText
        // e.g. "List<User>" → "User", "Set<Tag>" → "Tag", "User" → "User"
        val generic = Regex("""<(\w+)>""").find(typeText)?.groupValues?.get(1)
        return generic ?: typeText.trim()
    }
}

package com.github.pablolec.play1toolkit.playjpa.documentation

import com.github.pablolec.play1toolkit.playjpa.model.PlayJpaModelInfo
import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.project.DumbService
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil

class PlayJpaDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (DumbService.isDumb(file.project)) return emptyList()
        val element = file.findElementAt(offset) ?: return emptyList()
        val project = file.project

        val psiClass = element.parent as? PsiClass
            ?: PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        if (psiClass != null && PlayJpaModelUtils.isPlayJpaModel(psiClass)) {
            val svc = PlayJpaModelService.getInstance(project)
            val info = svc.findModelForClass(psiClass) ?: return emptyList()
            return listOf(
                PlayJpaModelDocTarget(
                    SmartPointerManager.createPointer(psiClass),
                    buildModelDoc(info)
                )
            )
        }

        val psiField = element.parent as? PsiField
            ?: PsiTreeUtil.getParentOfType(element, PsiField::class.java)
        if (psiField != null) {
            val annNames = PlayJpaModelUtils.getJpaAnnotations(psiField)
            val relationAnn = annNames.firstOrNull { PlayJpaModelUtils.isRelationAnnotation(it) }
            if (relationAnn != null) {
                val targetType = psiField.type.presentableText.let { t ->
                    Regex("""<(\w+)>""").find(t)?.groupValues?.get(1) ?: t
                }
                return listOf(
                    PlayJpaRelationDocTarget(
                        SmartPointerManager.createPointer(psiField),
                        buildRelationDoc(psiField.name, psiField.type.presentableText, relationAnn, targetType, annNames)
                    )
                )
            }
        }

        return emptyList()
    }

    private fun buildModelDoc(info: PlayJpaModelInfo): String {
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>Play JPA model</b>")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<table>")
            append("<tr><td><b>Class:</b></td><td>${info.qualifiedName ?: info.className}</td></tr>")
            append("<tr><td><b>Source:</b></td><td>${info.sourceKind.name.lowercase().replace('_', ' ')}</td></tr>")
            info.idField?.let { append("<tr><td><b>Identifier:</b></td><td>${it.name} : ${it.typeText}</td></tr>") }
            if (info.fields.isNotEmpty()) {
                val fieldList = info.fields.joinToString(", ") { "${it.name} : ${it.typeText}" }
                append("<tr><td><b>Fields:</b></td><td>$fieldList</td></tr>")
            }
            if (info.relations.isNotEmpty()) {
                val relList = info.relations.joinToString(", ") { rel ->
                    "${rel.fieldName} (${rel.relationKind.name.lowercase().replace("_", "-")} → ${rel.targetModel ?: "?"})"
                }
                append("<tr><td><b>Relations:</b></td><td>$relList</td></tr>")
            }
            append("</table>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildRelationDoc(
        fieldName: String,
        typeText: String,
        relationAnn: String,
        targetType: String,
        allAnns: List<String>
    ): String {
        val mappedBy = allAnns.firstOrNull { it.startsWith("mappedBy") }
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>JPA relation</b>")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<table>")
            append("<tr><td><b>Field:</b></td><td>$fieldName : $typeText</td></tr>")
            append("<tr><td><b>Relation:</b></td><td>@$relationAnn</td></tr>")
            append("<tr><td><b>Target:</b></td><td>$targetType</td></tr>")
            if (mappedBy != null) append("<tr><td><b>Mapped by:</b></td><td>$mappedBy</td></tr>")
            append("</table>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }
}

private class PlayJpaModelDocTarget(
    private val pointer: com.intellij.psi.SmartPsiElementPointer<PsiClass>,
    private val html: String
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> =
        Pointer { PlayJpaModelDocTarget(pointer, html) }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(pointer.element?.name ?: "Play JPA model").presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(html)

    override fun computeDocumentationHint(): String? = null
}

private class PlayJpaRelationDocTarget(
    private val pointer: com.intellij.psi.SmartPsiElementPointer<PsiField>,
    private val html: String
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> =
        Pointer { PlayJpaRelationDocTarget(pointer, html) }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(pointer.element?.name ?: "JPA relation").presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(html)

    override fun computeDocumentationHint(): String? = null
}

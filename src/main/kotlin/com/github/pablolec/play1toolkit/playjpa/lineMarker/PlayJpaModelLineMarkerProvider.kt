package com.github.pablolec.play1toolkit.playjpa.lineMarker

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.playjpa.util.PlayJpaModelUtils
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIdentifier

class PlayJpaModelLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        for (element in elements) {
            if (element !is PsiIdentifier) continue
            val parent = element.parent
            when (parent) {
                is PsiClass -> {
                    if (!DumbService.isDumb(element.project) && PlayJpaModelUtils.isPlayJpaModel(parent)) {
                        result.add(createModelClassMarker(element, parent))
                    }
                }
                is PsiField -> {
                    val annNames = PlayJpaModelUtils.getJpaAnnotations(parent)
                    if (annNames.any { PlayJpaModelUtils.isRelationAnnotation(it) }) {
                        result.add(createRelationFieldMarker(element, parent, annNames))
                    }
                }
            }
        }
    }

    private fun createModelClassMarker(identifier: PsiIdentifier, psiClass: PsiClass): LineMarkerInfo<PsiElement> {
        val svc = PlayJpaModelService.getInstance(psiClass.project)
        return LineMarkerInfo(
            identifier,
            identifier.textRange,
            AllIcons.Nodes.DataTables,
            { elem ->
                val info = svc.findModelForClass(elem.parent as? PsiClass ?: return@LineMarkerInfo "Play JPA model")
                if (info == null) return@LineMarkerInfo "Play JPA model"
                val fieldNames = info.fields.joinToString(", ") { it.name }
                val relNames = info.relations.joinToString(", ") { it.fieldName }
                buildString {
                    append("Play JPA model")
                    if (fieldNames.isNotBlank()) append("\nFields: $fieldNames")
                    if (relNames.isNotBlank()) append("\nRelations: $relNames")
                }
            },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { "Play JPA model" }
        )
    }

    private fun createRelationFieldMarker(
        identifier: PsiIdentifier,
        field: PsiField,
        annNames: List<String>
    ): LineMarkerInfo<PsiElement> {
        val relationKind = annNames.firstOrNull { PlayJpaModelUtils.isRelationAnnotation(it) } ?: "Relation"
        val targetType = field.type.presentableText.let { t ->
            Regex("""<(\w+)>""").find(t)?.groupValues?.get(1) ?: t
        }
        return LineMarkerInfo(
            identifier,
            identifier.textRange,
            AllIcons.Nodes.Related,
            { "JPA relation: ${relationKind.lowercase().replace("tomany", " to many").replace("toone", " to one")} to $targetType" },
            { _, elem ->
                val fieldParent = elem.parent as? PsiField ?: return@LineMarkerInfo
                val project = fieldParent.project
                val svc = PlayJpaModelService.getInstance(project)
                val targetModel = svc.findModelByName(targetType) ?: return@LineMarkerInfo
                targetModel.psiClass.navigate(true)
            },
            GutterIconRenderer.Alignment.LEFT,
            { "JPA relation to $targetType" }
        )
    }
}

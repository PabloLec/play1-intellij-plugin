package com.github.pablolec.play1toolkit.playconfig.references

import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigProperty
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

/**
 * Soft PSI reference from a Java string literal to the corresponding PlayConfigProperty.
 *
 * Resolves to the effective property for the active profile, or all available
 * definitions if no active profile is known.
 */
class PlayConfigStringLiteralReference(
    element: PsiLiteralExpression,
    range: TextRange
) : PsiReferenceBase.Poly<PsiLiteralExpression>(element, range, true) {

    private val configKey: String get() = element.value?.toString() ?: ""

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val key = configKey
        if (key.isBlank()) return ResolveResult.EMPTY_ARRAY

        val svc = PlayConfigService.getInstance(element.project)
        val resolution = svc.resolve(key)
        val activeProfile = resolution.activeProfile

        val targets = if (activeProfile != null) {
            listOfNotNull(resolution.profileValue?.property, resolution.defaultValue?.property)
        } else {
            svc.keysForLogical(key).map { it.property }
        }

        return targets.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun resolve(): PsiElement? = multiResolve(false).firstOrNull()?.element

    override fun getVariants(): Array<Any> = emptyArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        val literalValue = element.value as? String ?: return element
        val oldKey = literalValue
        val newText = "\"$newElementName\""
        val factory = JavaPsiFacade.getElementFactory(element.project)
        val newLiteral = factory.createExpressionFromText(newText, element)
        return element.replace(newLiteral)
    }
}

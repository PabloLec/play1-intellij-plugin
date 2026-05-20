package com.github.pablolec.play1toolkit.playconfig.references

import com.github.pablolec.play1toolkit.playconfig.model.PlayConfigWrapperMethod
import com.github.pablolec.play1toolkit.playconfig.settings.PlayConfigProjectSettings
import com.intellij.psi.*

/**
 * Determines whether a PsiLiteralExpression is used as a Play config key.
 * Returns the literal if it's in a recognized context, null otherwise.
 */
object PlayConfigContextDetector {

    private val PLAY_CONFIG_METHODS = setOf("getProperty", "get")
    private val PLAY_CONFIG_RECEIVERS = setOf("configuration", "Play")

    private val ANNOTATION_QUALIFIED_NAMES = setOf(
        "play.jobs.On", "play.jobs.Every"
    )

    private val PROBABLE_METHODS = setOf("getProperty")

    fun isConfigKeyContext(literal: PsiLiteralExpression): Boolean {
        return isDirectPlayConfigCall(literal)
            || isAnnotationContext(literal)
            || isWrapperContext(literal)
    }

    fun isDirectPlayConfigCall(literal: PsiLiteralExpression): Boolean {
        val arg = literal.parent as? PsiExpressionList ?: return false
        val call = arg.parent as? PsiMethodCallExpression ?: return false
        val method = call.methodExpression
        val methodName = method.referenceName ?: return false
        if (methodName !in PLAY_CONFIG_METHODS) return false

        // Check receiver chain: Play.configuration.getProperty / configuration.getProperty
        val qualifier = method.qualifierExpression
        if (qualifier == null) return false

        val qualText = qualifier.text
        return qualText.contains("Play.configuration") || qualText.contains("play.Play.configuration")
            || qualText == "configuration" || qualText == "config"
    }

    fun isProbableConfigCall(literal: PsiLiteralExpression): Boolean {
        val arg = literal.parent as? PsiExpressionList ?: return false
        val call = arg.parent as? PsiMethodCallExpression ?: return false
        val methodName = call.methodExpression.referenceName ?: return false
        if (methodName !in PROBABLE_METHODS) return false

        // System.getProperty / properties.getProperty / *.getProperty — mark as probable
        val qualifier = call.methodExpression.qualifierExpression ?: return false
        val qualText = qualifier.text
        return qualText == "System" || qualText.endsWith("properties") || qualText.endsWith("config")
    }

    fun isAnnotationContext(literal: PsiLiteralExpression): Boolean {
        val annotationAttributeValue = literal.parent as? PsiAnnotationMemberValue ?: return false
        val annotation = generateSequence(literal.parent) { it.parent }
            .filterIsInstance<PsiAnnotation>()
            .firstOrNull() ?: return false
        return annotation.qualifiedName in ANNOTATION_QUALIFIED_NAMES
    }

    fun isWrapperContext(literal: PsiLiteralExpression): Boolean {
        val project = literal.project
        val settings = PlayConfigProjectSettings.getInstance(project)
        val wrappers = settings.wrapperMethods
        if (wrappers.isEmpty()) return false

        val arg = literal.parent as? PsiExpressionList ?: return false
        val call = arg.parent as? PsiMethodCallExpression ?: return false
        val methodName = call.methodExpression.referenceName ?: return false
        val argIndex = arg.expressions.indexOf(literal)

        return wrappers.any { w ->
            w.methodName == methodName && w.keyArgIndex == argIndex &&
                isCallToClass(call, w.fqClassName)
        }
    }

    fun getArgIndex(literal: PsiLiteralExpression): Int {
        val arg = literal.parent as? PsiExpressionList ?: return 0
        return arg.expressions.indexOf(literal)
    }

    private fun isCallToClass(call: PsiMethodCallExpression, fqClassName: String): Boolean {
        val resolved = call.resolveMethod() ?: return false
        return resolved.containingClass?.qualifiedName == fqClassName
    }
}

package com.github.pablolec.play1toolkit.response

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil

class PlayActionResponseAnalyzer(private val project: Project) {

    fun analyze(method: PsiMethod): PlayEndpointResponseInfo {
        if (!isPlayActionMethod(method)) {
            return PlayEndpointResponseInfo(PlayResponseKind.UNKNOWN, emptyList(), PlayResponseConfidence.LOW)
        }

        val body = method.body ?: return PlayEndpointResponseInfo(
            PlayResponseKind.UNKNOWN,
            emptyList(),
            PlayResponseConfidence.LOW
        )

        val outcomes = mutableListOf<PlayResponseOutcome>()
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                buildOutcome(method, expression)?.let(outcomes::add)
            }
        })

        val distinctPrimaryKinds = outcomes
            .filter { it.kind !in setOf(PlayResponseKind.STATUS, PlayResponseKind.ERROR, PlayResponseKind.UNKNOWN) }
            .map { it.kind }
            .distinct()

        val kind = when {
            distinctPrimaryKinds.size > 1 -> PlayResponseKind.MIXED
            distinctPrimaryKinds.size == 1 -> distinctPrimaryKinds.first()
            outcomes.any { it.kind == PlayResponseKind.ERROR } -> PlayResponseKind.ERROR
            outcomes.any { it.kind == PlayResponseKind.STATUS } -> PlayResponseKind.STATUS
            outcomes.isNotEmpty() -> outcomes.first().kind
            else -> PlayResponseKind.UNKNOWN
        }

        val confidence = when {
            outcomes.isEmpty() -> PlayResponseConfidence.LOW
            outcomes.any { it.confidence == PlayResponseConfidence.MEDIUM } -> PlayResponseConfidence.MEDIUM
            else -> PlayResponseConfidence.HIGH
        }

        return PlayEndpointResponseInfo(kind, outcomes, confidence)
    }

    fun isPlayActionMethod(method: PsiMethod): Boolean {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false
        if (!method.hasModifierProperty(PsiModifier.STATIC)) return false
        if (method.returnType?.equalsToText("void") != true) return false
        val containingClass = method.containingClass ?: return false
        return Play1ViewUtils.isPlayControllerClass(containingClass)
    }

    private fun buildOutcome(actionMethod: PsiMethod, call: PsiMethodCallExpression): PlayResponseOutcome? {
        val resolved = call.resolveMethod()
        val methodName = call.methodExpression.referenceName ?: return null
        if (!isSupportedPlayResponseCall(resolved, methodName)) return null

        val sourceElement = call.methodExpression.referenceNameElement ?: call
        val args = call.argumentList.expressions
        val confidence = if (resolved != null) PlayResponseConfidence.HIGH else PlayResponseConfidence.MEDIUM

        return when (methodName) {
            "render", "renderTemplate" -> {
                val template = inferHtmlTemplate(actionMethod, args, methodName == "renderTemplate")
                PlayResponseOutcome(
                    kind = PlayResponseKind.HTML,
                    sourceElement = sourceElement,
                    details = "HTML template: $template",
                    callText = buildCallText(methodName, args),
                    confidence = confidence
                )
            }

            "renderJSON" -> {
                val jsonType = args.firstOrNull()?.let(::describeType) ?: "JSON"
                PlayResponseOutcome(
                    kind = PlayResponseKind.JSON,
                    sourceElement = sourceElement,
                    details = jsonType,
                    callText = buildCallText(methodName, args),
                    confidence = confidence
                )
            }

            "renderXml" -> PlayResponseOutcome(
                kind = PlayResponseKind.XML,
                sourceElement = sourceElement,
                details = "XML response",
                callText = buildCallText(methodName, args),
                confidence = confidence
            )

            "renderText" -> PlayResponseOutcome(
                kind = PlayResponseKind.TEXT,
                sourceElement = sourceElement,
                details = "Plain text response",
                callText = buildCallText(methodName, args),
                confidence = confidence
            )

            "renderBinary" -> PlayResponseOutcome(
                kind = PlayResponseKind.BINARY,
                sourceElement = sourceElement,
                details = "Binary response",
                callText = buildCallText(methodName, args),
                confidence = confidence
            )

            "redirect", "redirectToStatic" -> PlayResponseOutcome(
                kind = PlayResponseKind.REDIRECT,
                sourceElement = sourceElement,
                details = "Redirect response",
                callText = buildCallText(methodName, args),
                confidence = confidence
            )

            "error" -> PlayResponseOutcome(
                kind = PlayResponseKind.ERROR,
                sourceElement = sourceElement,
                details = "HTTP 500",
                callText = buildCallText(methodName, args),
                statusCode = 500,
                confidence = confidence
            )

            else -> {
                val statusCode = statusCodeFor(methodName)
                PlayResponseOutcome(
                    kind = PlayResponseKind.STATUS,
                    sourceElement = sourceElement,
                    details = "HTTP $statusCode",
                    callText = buildCallText(methodName, args),
                    statusCode = statusCode,
                    confidence = confidence
                )
            }
        }
    }

    private fun isSupportedPlayResponseCall(resolved: PsiMethod?, methodName: String): Boolean {
        if (methodName !in SUPPORTED_METHODS) return false
        if (resolved == null) return true
        val containingClass = resolved.containingClass ?: return true
        return containingClass.qualifiedName == "play.mvc.Controller" ||
            InheritanceUtil.isInheritor(containingClass, "play.mvc.Controller")
    }

    private fun inferHtmlTemplate(actionMethod: PsiMethod, args: Array<PsiExpression>, explicitOnly: Boolean): String {
        val explicit = extractTemplatePath(args.firstOrNull())
        if (explicit != null) return explicit
        if (explicitOnly) return "dynamic"

        val controller = actionMethod.containingClass ?: return "dynamic"
        return implicitTemplatePath(controller, actionMethod.name)
    }

    private fun extractTemplatePath(expression: PsiExpression?): String? {
        val value = (expression as? PsiLiteralExpression)?.value as? String ?: return null
        val normalized = value.removePrefix("/")
        return if (normalized.endsWith(".html") || normalized.endsWith(".groovy")) normalized else null
    }

    private fun implicitTemplatePath(controllerClass: PsiClass, actionName: String): String {
        val qualifiedName = controllerClass.qualifiedName ?: controllerClass.name ?: return "dynamic"
        val relativeController = qualifiedName.removePrefix("controllers.").replace('.', '/')
        return "$relativeController/$actionName.html"
    }

    private fun describeType(expression: PsiExpression): String {
        val type = expression.type ?: return "JSON"
        return "JSON<${presentableType(type)}>"
    }

    private fun presentableType(type: PsiType): String {
        val text = type.presentableText
        return if (text.isNotBlank()) text else type.canonicalText
    }

    private fun buildCallText(methodName: String, args: Array<PsiExpression>): String {
        val renderedArgs = args.joinToString(", ") { it.text.take(80) }
        return "$methodName($renderedArgs)"
    }

    private fun statusCodeFor(methodName: String): Int = when (methodName) {
        "ok" -> 200
        "badRequest" -> 400
        "unauthorized" -> 401
        "forbidden" -> 403
        "notFound", "todo" -> 404
        "notModified" -> 304
        else -> 0
    }

    companion object {
        private val SUPPORTED_METHODS = setOf(
            "render",
            "renderTemplate",
            "renderJSON",
            "renderXml",
            "renderText",
            "renderBinary",
            "redirect",
            "redirectToStatic",
            "notFound",
            "forbidden",
            "unauthorized",
            "badRequest",
            "ok",
            "error",
            "notModified",
            "todo",
        )
    }
}

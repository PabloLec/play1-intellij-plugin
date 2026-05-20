package com.github.pablolec.play1toolkit.response

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiThrowStatement
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil

class PlayActionResponseAnalyzer(private val project: Project) {

    fun analyze(method: PsiMethod): PlayEndpointResponseInfo {
        if (!isPlayActionMethod(method)) {
            return PlayEndpointResponseInfo(PlayResponseKind.UNKNOWN, emptyList(), PlayResponseConfidence.LOW)
        }

        if (method.body == null) {
            return PlayEndpointResponseInfo(PlayResponseKind.UNKNOWN, emptyList(), PlayResponseConfidence.LOW)
        }

        val outcomes = linkedMapOf<String, PlayResponseOutcome>()
        collectOutcomes(method, method, 0, linkedSetOf(), outcomes)

        val orderedOutcomes = outcomes.values.toList()
        val distinctPrimaryKinds = orderedOutcomes
            .filter { it.kind in PlayEndpointResponseInfo.PRIMARY_KINDS }
            .map { it.kind }
            .distinct()

        val kind = when {
            distinctPrimaryKinds.size > 1 -> PlayResponseKind.MIXED
            distinctPrimaryKinds.size == 1 -> distinctPrimaryKinds.first()
            orderedOutcomes.any { it.kind == PlayResponseKind.STATUS || it.kind == PlayResponseKind.ERROR } ->
                PlayResponseKind.STATUS
            else -> PlayResponseKind.UNKNOWN
        }

        val confidence = when {
            orderedOutcomes.isEmpty() -> PlayResponseConfidence.LOW
            orderedOutcomes.any { it.confidence == PlayResponseConfidence.MEDIUM } -> PlayResponseConfidence.MEDIUM
            else -> PlayResponseConfidence.HIGH
        }

        return PlayEndpointResponseInfo(kind, orderedOutcomes, confidence)
    }

    fun isPlayActionMethod(method: PsiMethod): Boolean {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false
        if (method.returnType?.equalsToText("void") != true) return false
        val containingClass = method.containingClass ?: return false
        return Play1ViewUtils.isPlayControllerClass(containingClass)
    }

    private fun collectOutcomes(
        actionMethod: PsiMethod,
        currentMethod: PsiMethod,
        depth: Int,
        visiting: MutableSet<PsiMethod>,
        outcomes: MutableMap<String, PlayResponseOutcome>,
    ) {
        if (depth > MAX_CALL_DEPTH) return
        val body = currentMethod.body ?: return
        if (!visiting.add(currentMethod)) return

        try {
            body.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)
                    val resolved = expression.resolveMethod()
                    buildCallOutcome(actionMethod, expression, resolved)?.let { outcome ->
                        outcomes.putIfAbsent(outcomeKey(outcome), outcome)
                        return
                    }
                    if (resolved != null && shouldTraverse(resolved, visiting)) {
                        collectOutcomes(actionMethod, resolved, depth + 1, visiting, outcomes)
                    }
                }

                override fun visitThrowStatement(statement: PsiThrowStatement) {
                    super.visitThrowStatement(statement)
                    buildThrowOutcome(actionMethod, statement.exception)?.let { outcome ->
                        outcomes.putIfAbsent(outcomeKey(outcome), outcome)
                    }
                }

                override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
                    super.visitAssignmentExpression(expression)
                    buildStatusAssignmentOutcome(expression)?.let { outcome ->
                        outcomes.putIfAbsent(outcomeKey(outcome), outcome)
                    }
                }
            })
        } finally {
            visiting.remove(currentMethod)
        }
    }

    private fun buildCallOutcome(
        actionMethod: PsiMethod,
        call: PsiMethodCallExpression,
        resolved: PsiMethod?,
    ): PlayResponseOutcome? {
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
                    confidence = confidence,
                )
            }

            "renderJSON" -> PlayResponseOutcome(
                kind = PlayResponseKind.JSON,
                sourceElement = sourceElement,
                details = args.firstOrNull()?.let(::describeType) ?: "JSON",
                callText = buildCallText(methodName, args),
                confidence = confidence,
            )

            "renderXml" -> PlayResponseOutcome(
                kind = PlayResponseKind.XML,
                sourceElement = sourceElement,
                details = "XML response",
                callText = buildCallText(methodName, args),
                confidence = confidence,
            )

            "renderText" -> PlayResponseOutcome(
                kind = PlayResponseKind.TEXT,
                sourceElement = sourceElement,
                details = "Plain text response",
                callText = buildCallText(methodName, args),
                confidence = confidence,
            )

            "renderBinary" -> PlayResponseOutcome(
                kind = PlayResponseKind.BINARY,
                sourceElement = sourceElement,
                details = "Binary response",
                callText = buildCallText(methodName, args),
                confidence = confidence,
            )

            "redirect", "redirectToStatic" -> PlayResponseOutcome(
                kind = PlayResponseKind.REDIRECT,
                sourceElement = sourceElement,
                details = "Redirect response",
                callText = buildCallText(methodName, args),
                confidence = confidence,
            )

            "error" -> statusOutcome(
                sourceElement = sourceElement,
                statusCode = extractStatusCode(args.firstOrNull()) ?: 500,
                callText = buildCallText(methodName, args),
                confidence = confidence,
            )

            else -> statusOutcome(
                sourceElement = sourceElement,
                statusCode = statusCodeFor(methodName),
                callText = buildCallText(methodName, args),
                confidence = confidence,
            )
        }
    }

    private fun buildThrowOutcome(actionMethod: PsiMethod, exception: PsiExpression?): PlayResponseOutcome? {
        val newExpression = exception as? PsiNewExpression ?: return null
        val className = newExpression.classReference?.referenceName ?: return null
        val resolvedClass = newExpression.classReference?.resolve() as? PsiClass
        val qualifiedName = resolvedClass?.qualifiedName
        if (!isSupportedPlayResultThrow(className, qualifiedName)) return null

        val sourceElement = newExpression.classReference?.referenceNameElement ?: newExpression
        val args = newExpression.argumentList?.expressions.orEmpty()
        val confidence = if (resolvedClass != null) PlayResponseConfidence.HIGH else PlayResponseConfidence.MEDIUM
        val throwText = buildThrowText(className, args)

        return when (className) {
            "RenderJson" -> PlayResponseOutcome(
                kind = PlayResponseKind.JSON,
                sourceElement = sourceElement,
                details = args.firstOrNull()?.let(::describeType) ?: "JSON",
                callText = throwText,
                confidence = confidence,
            )

            "RenderText" -> PlayResponseOutcome(
                kind = PlayResponseKind.TEXT,
                sourceElement = sourceElement,
                details = "Plain text response",
                callText = throwText,
                confidence = confidence,
            )

            "RenderBinary" -> PlayResponseOutcome(
                kind = PlayResponseKind.BINARY,
                sourceElement = sourceElement,
                details = "Binary response",
                callText = throwText,
                confidence = confidence,
            )

            "RenderXml" -> PlayResponseOutcome(
                kind = PlayResponseKind.XML,
                sourceElement = sourceElement,
                details = "XML response",
                callText = throwText,
                confidence = confidence,
            )

            "RenderTemplate" -> PlayResponseOutcome(
                kind = PlayResponseKind.HTML,
                sourceElement = sourceElement,
                details = "HTML template: ${inferThrownTemplate(actionMethod, args)}",
                callText = throwText,
                confidence = confidence,
            )

            "Redirect" -> PlayResponseOutcome(
                kind = PlayResponseKind.REDIRECT,
                sourceElement = sourceElement,
                details = "Redirect response",
                callText = throwText,
                confidence = confidence,
            )

            "NotFound" -> statusOutcome(sourceElement, 404, throwText, confidence)
            "BadRequest" -> statusOutcome(sourceElement, 400, throwText, confidence)
            "Forbidden" -> statusOutcome(sourceElement, 403, throwText, confidence)
            "Unauthorized" -> statusOutcome(sourceElement, 401, throwText, confidence)
            "Error" -> statusOutcome(sourceElement, extractStatusCode(args.firstOrNull()) ?: 500, throwText, confidence)
            else -> null
        }
    }

    private fun buildStatusAssignmentOutcome(expression: PsiAssignmentExpression): PlayResponseOutcome? {
        val left = expression.lExpression as? PsiReferenceExpression ?: return null
        if (left.referenceName != "status") return null
        val qualifier = left.qualifierExpression?.text ?: return null
        if (qualifier != "response") return null
        val statusCode = extractStatusCode(expression.rExpression) ?: return null
        return statusOutcome(
            sourceElement = left.referenceNameElement ?: expression,
            statusCode = statusCode,
            callText = "response.status = ${expression.rExpression?.text.orEmpty()}",
            confidence = PlayResponseConfidence.HIGH,
        )
    }

    private fun statusOutcome(
        sourceElement: PsiElement,
        statusCode: Int,
        callText: String,
        confidence: PlayResponseConfidence,
    ) = PlayResponseOutcome(
        kind = PlayResponseKind.STATUS,
        sourceElement = sourceElement,
        details = "HTTP $statusCode",
        callText = callText,
        statusCode = statusCode,
        confidence = confidence,
    )

    private fun isSupportedPlayResponseCall(resolved: PsiMethod?, methodName: String): Boolean {
        if (methodName !in SUPPORTED_METHODS) return false
        if (resolved == null) return true
        val containingClass = resolved.containingClass ?: return true
        return containingClass.qualifiedName == "play.mvc.Controller" ||
            InheritanceUtil.isInheritor(containingClass, "play.mvc.Controller")
    }

    private fun isSupportedPlayResultThrow(className: String, qualifiedName: String?): Boolean {
        if (className !in SUPPORTED_THROW_RESULTS) return false
        return qualifiedName == null ||
            qualifiedName.startsWith("play.mvc.results.") ||
            qualifiedName.startsWith("play.mvc.exceptions.")
    }

    private fun shouldTraverse(resolved: PsiMethod, visiting: Set<PsiMethod>): Boolean {
        if (resolved in visiting) return false
        if (resolved.isConstructor) return false
        if (resolved.body == null) return false
        if (resolved.containingClass?.qualifiedName == "play.mvc.Controller") return false
        if (isSupportedPlayResponseCall(resolved, resolved.name)) return false
        return isProjectSourceFile(resolved.containingFile)
    }

    private fun isProjectSourceFile(file: PsiFile?): Boolean {
        val virtualFile = file?.virtualFile ?: return false
        return ProjectFileIndex.getInstance(project).isInSourceContent(virtualFile)
    }

    private fun outcomeKey(outcome: PlayResponseOutcome): String {
        val filePath = outcome.sourceElement.containingFile?.virtualFile?.path.orEmpty()
        val offset = outcome.sourceElement.textRange.startOffset
        return "${outcome.kind}|$filePath|$offset|${outcome.callText.orEmpty()}"
    }

    private fun inferHtmlTemplate(actionMethod: PsiMethod, args: Array<PsiExpression>, explicitOnly: Boolean): String {
        val explicit = extractTemplatePath(args.firstOrNull())
        if (explicit != null) return explicit
        if (explicitOnly) return "dynamic"

        val controller = actionMethod.containingClass ?: return "dynamic"
        return implicitTemplatePath(controller, actionMethod.name)
    }

    private fun inferThrownTemplate(actionMethod: PsiMethod, args: Array<out PsiExpression>): String {
        val explicit = extractTemplatePath(args.firstOrNull())
        return explicit ?: inferHtmlTemplate(actionMethod, emptyArray(), explicitOnly = false)
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

    private fun buildThrowText(className: String, args: Array<out PsiExpression>): String {
        val renderedArgs = args.joinToString(", ") { it.text.take(80) }
        return "throw new $className($renderedArgs)"
    }

    private fun extractStatusCode(expression: PsiExpression?): Int? {
        val value = expression?.let { JavaPsiFacade.getInstance(project).constantEvaluationHelper.computeConstantExpression(it) }
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            else -> null
        }
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
        private const val MAX_CALL_DEPTH = 8

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

        private val SUPPORTED_THROW_RESULTS = setOf(
            "RenderJson",
            "RenderText",
            "RenderBinary",
            "RenderXml",
            "RenderTemplate",
            "Redirect",
            "NotFound",
            "BadRequest",
            "Forbidden",
            "Unauthorized",
            "Error",
        )
    }
}

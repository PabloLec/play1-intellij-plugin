package com.github.pablolec.play1toolkit.response

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
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

        if (method.body == null) return PlayEndpointResponseInfo(
            PlayResponseKind.UNKNOWN,
            emptyList(),
            PlayResponseConfidence.LOW
        )

        val outcomes = linkedMapOf<String, PlayResponseOutcome>()
        collectOutcomes(method, method, 0, linkedSetOf(), outcomes)

        val orderedOutcomes = outcomes.values.toList()
        val distinctPrimaryKinds = orderedOutcomes
            .filter { it.kind !in setOf(PlayResponseKind.STATUS, PlayResponseKind.ERROR, PlayResponseKind.UNKNOWN) }
            .map { it.kind }
            .distinct()

        val kind = when {
            distinctPrimaryKinds.size > 1 -> PlayResponseKind.MIXED
            distinctPrimaryKinds.size == 1 -> distinctPrimaryKinds.first()
            orderedOutcomes.any { it.kind == PlayResponseKind.ERROR } -> PlayResponseKind.ERROR
            orderedOutcomes.any { it.kind == PlayResponseKind.STATUS } -> PlayResponseKind.STATUS
            orderedOutcomes.isNotEmpty() -> orderedOutcomes.first().kind
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
        if (!method.hasModifierProperty(PsiModifier.STATIC)) return false
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
                    buildOutcome(actionMethod, expression, resolved)?.let { outcome ->
                        outcomes.putIfAbsent(outcomeKey(outcome), outcome)
                        return
                    }
                    if (resolved != null && shouldTraverse(resolved, visiting)) {
                        collectOutcomes(actionMethod, resolved, depth + 1, visiting, outcomes)
                    }
                }
            })
        } finally {
            visiting.remove(currentMethod)
        }
    }

    private fun buildOutcome(
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
        private const val MAX_CALL_DEPTH = 6

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

package com.github.pablolec.play1toolkit.playcache.util

import com.github.pablolec.play1toolkit.playcache.model.PlayCacheKey
import com.github.pablolec.play1toolkit.playcache.model.PlayCacheTtl
import com.github.pablolec.play1toolkit.playcache.model.PlayCachedTemplateFragment
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigFile
import com.github.pablolec.play1toolkit.playconfig.service.PlayConfigService
import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiStatement
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

data class PlayCacheResolvedTemplateValue(
    val displayText: String,
    val resolvedValue: String? = null,
    val sourceVariable: String? = null,
    val configurationKey: String? = null,
    val configurationValue: String? = null,
    val booleanValue: Boolean? = null,
    val sourceElement: PsiElement? = null
)

object PlayCacheTemplateValueResolver {
    private val SINGLE_INTERPOLATION = Regex("""^\s*\$\{([A-Za-z_]\w*)}\s*$""")
    private val SIMPLE_IDENTIFIER = Regex("""^[A-Za-z_]\w*$""")
    private val CONFIG_GET_PROPERTY = Regex("""(?:play\.)?Play\.configuration\.getProperty\(\s*["']([^"']+)["'](?:\s*,\s*["']([^"']*)["'])?\s*\)""")
    private val BUILDER_DYNAMIC_PREFIX = "\${...}"

    fun resolveKey(project: Project, fragment: PlayCachedTemplateFragment): PlayCacheResolvedTemplateValue =
        directConvention(project, fragment, "cacheName")
            ?: resolve(project, fragment.templateFile, fragment.key, fragment.rawKeyText, isTtl = false)

    fun resolveTtl(project: Project, fragment: PlayCachedTemplateFragment): PlayCacheResolvedTemplateValue =
        directConvention(project, fragment, "cacheExpiration")
            ?: resolve(project, fragment.templateFile, fragment.ttl, fragment.rawTtlText, isTtl = true)

    fun resolveGuard(project: Project, file: com.intellij.psi.PsiFile): PlayCacheResolvedTemplateValue? {
        val text = file.text ?: return null
        if (!text.contains("isCached")) return null
        resolveTemplateConventionFromPath(project, file, "isCached")?.let { return it }
        val controllerValue = resolveTemplateVariable(project, file, "isCached") ?: return null
        return controllerValue
    }

    fun resolveInjectedVariable(
        project: Project,
        file: com.intellij.psi.PsiFile,
        variableName: String
    ): PlayCacheResolvedTemplateValue? =
        resolveTemplateVariable(project, file, variableName)

    private fun directConvention(
        project: Project,
        fragment: PlayCachedTemplateFragment,
        variableName: String
    ): PlayCacheResolvedTemplateValue? {
        val raw = when (variableName) {
            "cacheName" -> fragment.rawKeyText
            "cacheExpiration" -> fragment.rawTtlText
            else -> null
        } ?: return null
        if (!raw.contains(variableName)) return null
        val convention = resolveTemplateConventionFromPath(project, fragment.templateFile, variableName) ?: return null
        val fallback = when (variableName) {
            "cacheName" -> defaultDisplay(fragment.key)
            "cacheExpiration" -> defaultDisplay(fragment.ttl)
            else -> "dynamic"
        }
        return convention.copy(displayText = buildResolvedDisplay(variableName, convention, fallback))
    }

    private fun resolve(
        project: Project,
        file: com.intellij.psi.PsiFile,
        parsed: Any,
        rawText: String?,
        isTtl: Boolean
    ): PlayCacheResolvedTemplateValue {
        val fallback = defaultDisplay(parsed)
        val raw = rawText?.trim().orEmpty()
        if (raw.isEmpty()) return PlayCacheResolvedTemplateValue(fallback)

        val variableName = referencedVariableName(raw) ?: variableNameFromParsed(parsed)
        val resolved = variableName?.let { resolveTemplateVariable(project, file, it) }
        if (resolved != null) {
            val display = buildResolvedDisplay(variableName, resolved, fallback)
            return resolved.copy(displayText = display)
        }

        val conventionResolved = variableName?.let { resolveTemplateConventionFromPath(project, file, it) }
        if (conventionResolved != null) {
            val display = buildResolvedDisplay(variableName, conventionResolved, fallback)
            return conventionResolved.copy(displayText = display)
        }

        val localResolved = resolveAssignedExpression(project, raw, emptyMap(), null)
        if (localResolved != null) {
            val display = buildResolvedDisplay(null, localResolved, fallback)
            return localResolved.copy(displayText = display)
        }

        return if (isTtl) PlayCacheResolvedTemplateValue(fallback) else PlayCacheResolvedTemplateValue(fallback)
    }

    private fun resolveTemplateVariable(
        project: Project,
        file: com.intellij.psi.PsiFile,
        variableName: String
    ): PlayCacheResolvedTemplateValue? {
        resolveLocalTemplateVariable(project, file, variableName)?.let { return it }

        val templateInfo = templateInfo(project, file) ?: return null
        val controllerName = templateInfo.controllerName
        val actionName = templateInfo.actionName
        val actionMethod = RoutesControllerResolver.resolveMethod(project, controllerName, actionName) ?: return null
        return resolveRenderArgValue(project, actionMethod, variableName)
            ?: resolveTemplateConvention(project, actionMethod, actionName, variableName)
    }

    private fun resolveTemplateConvention(
        project: Project,
        actionMethod: PsiMethod,
        actionName: String,
        variableName: String
    ): PlayCacheResolvedTemplateValue? {
        if (!usesDefaultTemplateCacheConvention(actionMethod)) return null
        return when (variableName) {
            "cacheExpiration" -> {
                val configKey = "cache.groovytemplate.delay"
                val value = PlayConfigService.getInstance(project).resolve(configKey).effectiveValue
                    ?: findConfigValueFallback(project, configKey)
                PlayCacheResolvedTemplateValue(
                    displayText = value ?: configKey,
                    configurationKey = configKey,
                    configurationValue = value,
                    resolvedValue = value,
                    sourceVariable = variableName
                )
            }
            "isCached" -> {
                val configKey = "cache.groovytemplate.enable"
                val value = PlayConfigService.getInstance(project).resolve(configKey).effectiveValue
                    ?: findConfigValueFallback(project, configKey)
                val enabled = value.equals("on", ignoreCase = true) || value.equals("true", ignoreCase = true)
                PlayCacheResolvedTemplateValue(
                    displayText = enabled.toString(),
                    configurationKey = configKey,
                    configurationValue = value,
                    booleanValue = enabled,
                    sourceVariable = variableName
                )
            }
            "cacheName" -> {
                val templateName = inferTemplateCacheNameArgument(actionMethod) ?: actionName
                PlayCacheResolvedTemplateValue(
                    displayText = "\${host}.\${httpPort}.$templateName",
                    resolvedValue = "\${host}.\${httpPort}.$templateName",
                    sourceVariable = variableName
                )
            }
            else -> null
        }
    }

    private fun resolveTemplateConventionFromPath(
        project: Project,
        file: com.intellij.psi.PsiFile,
        variableName: String
    ): PlayCacheResolvedTemplateValue? {
        val templateInfo = templateInfo(project, file) ?: return null
        val actionName = templateInfo.actionName
        return when (variableName) {
            "cacheExpiration" -> {
                val configKey = "cache.groovytemplate.delay"
                val value = PlayConfigService.getInstance(project).resolve(configKey).effectiveValue
                    ?: findConfigValueFallback(project, configKey)
                PlayCacheResolvedTemplateValue(
                    displayText = value ?: configKey,
                    configurationKey = configKey,
                    configurationValue = value,
                    resolvedValue = value,
                    sourceVariable = variableName
                )
            }
            "isCached" -> {
                val configKey = "cache.groovytemplate.enable"
                val value = PlayConfigService.getInstance(project).resolve(configKey).effectiveValue
                    ?: findConfigValueFallback(project, configKey)
                val enabled = value.equals("on", ignoreCase = true) || value.equals("true", ignoreCase = true)
                PlayCacheResolvedTemplateValue(
                    displayText = enabled.toString(),
                    configurationKey = configKey,
                    configurationValue = value,
                    booleanValue = enabled,
                    sourceVariable = variableName
                )
            }
            "cacheName" -> PlayCacheResolvedTemplateValue(
                displayText = "\${host}.\${httpPort}.$actionName",
                resolvedValue = "\${host}.\${httpPort}.$actionName",
                sourceVariable = variableName
            )
            else -> null
        }
    }

    private data class TemplateInfo(
        val logicalPath: String,
        val controllerName: String,
        val actionName: String
    )

    private fun templateInfo(project: Project, file: com.intellij.psi.PsiFile): TemplateInfo? {
        val logicalPath = file.virtualFile?.let { PlayTemplateFileUtils.logicalPath(project, it) }
            ?: inferLogicalPath(file)
            ?: return null
        val controllerName = PlayTemplateFileUtils.controllerNameFromLogicalPath(logicalPath) ?: return null
        val actionName = PlayTemplateFileUtils.actionNameFromLogicalPath(logicalPath) ?: return null
        return TemplateInfo(logicalPath, controllerName, actionName)
    }

    private fun inferLogicalPath(file: com.intellij.psi.PsiFile): String? {
        val normalizedPath = file.virtualFile?.path?.replace('\\', '/').orEmpty()
        val marker = "/app/views/"
        val markerIndex = normalizedPath.indexOf(marker)
        if (markerIndex >= 0) {
            return normalizedPath.substring(markerIndex + marker.length)
        }
        val fileName = file.name.takeIf { it.contains('.') } ?: return null
        val parentName = file.virtualFile?.parent?.name ?: return null
        return "$parentName/$fileName"
    }

    private fun usesDefaultTemplateCacheConvention(actionMethod: PsiMethod): Boolean =
        PsiTreeUtil.findChildrenOfType(actionMethod.body, PsiMethodCallExpression::class.java)
            .any { it.methodExpression.referenceName == "setDefaultParamsGroovyTemplate" || it.methodExpression.referenceName == "screen" }

    private fun inferTemplateCacheNameArgument(actionMethod: PsiMethod): String? {
        PsiTreeUtil.findChildrenOfType(actionMethod.body, PsiMethodCallExpression::class.java).forEach { call ->
            val name = call.methodExpression.referenceName
            if (name == "setDefaultParamsGroovyTemplate" || name == "screen") {
                val literal = call.argumentList.expressions.firstOrNull() as? PsiLiteralExpression
                val value = literal?.value as? String
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    private fun resolveLocalTemplateVariable(
        project: Project,
        file: com.intellij.psi.PsiFile,
        variableName: String
    ): PlayCacheResolvedTemplateValue? {
        val assignments = collectTemplateAssignments(file)
        val expr = assignments[variableName] ?: return null
        return resolveAssignedExpression(project, expr, emptyMap(), null)
            ?.copy(sourceVariable = variableName)
    }

    private fun collectTemplateAssignments(file: com.intellij.psi.PsiFile): Map<String, String> {
        val text = file.text ?: return emptyMap()
        val assignments = linkedMapOf<String, String>()
        PlayTemplatePatterns.SCRIPT_BLOCK.findAll(text).forEach { block ->
            block.groupValues[1]
                .lineSequence()
                .map { it.trim() }
                .filter { it.contains('=') }
                .forEach { line ->
                    parseTemplateAssignment(line)?.let { (name, expression) -> assignments[name] = expression }
                }
        }
        return assignments
    }

    private fun parseTemplateAssignment(line: String): Pair<String, String>? {
        val cleaned = line.removeSuffix(";").trim()
        val eq = cleaned.indexOf('=')
        if (eq <= 0) return null
        val left = cleaned.substring(0, eq).trim()
        val right = cleaned.substring(eq + 1).trim()
        val name = left.substringAfterLast(' ').trim()
        if (!SIMPLE_IDENTIFIER.matches(name) || right.isEmpty()) return null
        return name to right
    }

    private fun resolveRenderArgValue(project: Project, actionMethod: PsiMethod, variableName: String): PlayCacheResolvedTemplateValue? {
        val visited = mutableSetOf<String>()
        return resolveRenderArgValue(project, actionMethod, variableName, emptyMap(), visited, 0)
    }

    private fun resolveRenderArgValue(
        project: Project,
        method: PsiMethod,
        variableName: String,
        argumentBindings: Map<String, PsiExpression>,
        visited: MutableSet<String>,
        depth: Int
    ): PlayCacheResolvedTemplateValue? {
        if (depth > 6) return null
        val methodKey = method.containingClass?.qualifiedName + "#" + method.name + ":" + argumentBindings.keys.sorted()
        if (!visited.add(methodKey)) return null

        val statements = method.body?.statements.orEmpty()
        for (statement in statements) {
            for (call in PsiTreeUtil.findChildrenOfType(statement, PsiMethodCallExpression::class.java)) {
                if (isRenderArgsPut(call)) {
                    val args = call.argumentList.expressions
                    val key = evaluateStringExpression(args.getOrNull(0), argumentBindings, method, statement)
                    if (key == variableName) {
                        return resolveExpression(args.getOrNull(1), argumentBindings, method, statement)
                            ?.copy(sourceVariable = variableName, sourceElement = call)
                    }
                    continue
                }

                val resolvedMethod = call.resolveMethod() ?: continue
                if (!shouldFollowHelperCall(method, resolvedMethod)) continue
                val nestedBindings = bindArguments(resolvedMethod, call.argumentList.expressions, argumentBindings, method, statement)
                resolveRenderArgValue(project, resolvedMethod, variableName, nestedBindings, visited, depth + 1)
                    ?.let { return it }
            }
        }
        return null
    }

    private fun shouldFollowHelperCall(origin: PsiMethod, target: PsiMethod): Boolean {
        val originClass = origin.containingClass ?: return false
        val targetClass = target.containingClass ?: return false
        if (originClass == targetClass) return true
        val originQn = originClass.qualifiedName.orEmpty()
        val targetQn = targetClass.qualifiedName.orEmpty()
        if (originQn.startsWith("controllers.") && targetQn.startsWith("controllers.")) return true
        return originClass.isInheritor(targetClass, true) || targetClass.isInheritor(originClass, true)
    }

    private fun bindArguments(
        method: PsiMethod,
        args: Array<PsiExpression>,
        parentBindings: Map<String, PsiExpression>,
        parentMethod: PsiMethod,
        anchor: PsiElement
    ): Map<String, PsiExpression> {
        val out = linkedMapOf<String, PsiExpression>()
        method.parameterList.parameters.forEachIndexed { index, parameter ->
            val arg = args.getOrNull(index) ?: return@forEachIndexed
            out[parameter.name] = substituteExpression(arg, parentBindings, parentMethod, anchor)
        }
        return out
    }

    private fun substituteExpression(
        expression: PsiExpression,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod,
        anchor: PsiElement
    ): PsiExpression {
        if (expression is PsiReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is PsiParameter) {
                bindings[resolved.name]?.let { return it }
            }
        }
        return expression
    }

    private fun isRenderArgsPut(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != "put") return false
        return call.methodExpression.qualifierExpression?.text == "renderArgs"
    }

    private fun resolveAssignedExpression(
        project: Project,
        expressionText: String,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod?
    ): PlayCacheResolvedTemplateValue? {
        val expression = runCatching {
            JavaPsiFacade.getElementFactory(project).createExpressionFromText(expressionText, method)
        }.getOrNull() ?: return null
        return resolveExpression(expression, bindings, method, expression)
    }

    private fun resolveExpression(
        expression: PsiExpression?,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod?,
        anchor: PsiElement
    ): PlayCacheResolvedTemplateValue? {
        if (expression == null) return null
        val constant = constantString(expression)
        if (constant != null) {
            return PlayCacheResolvedTemplateValue(constant, resolvedValue = constant)
        }

        return when (expression) {
            is PsiLiteralExpression -> resolveLiteral(expression)
            is PsiReferenceExpression -> resolveReference(expression, bindings, method, anchor)
            is PsiMethodCallExpression -> resolveMethodCall(expression, bindings, method, anchor)
            is PsiPolyadicExpression -> resolvePolyadic(expression, bindings, method, anchor)
            is PsiBinaryExpression -> resolveBinary(expression, bindings, method, anchor)
            is PsiNewExpression -> resolveNewExpression(expression, bindings, method, anchor)
            else -> null
        }
    }

    private fun resolveLiteral(expression: PsiLiteralExpression): PlayCacheResolvedTemplateValue? {
        val value = expression.value ?: return null
        return when (value) {
            is String -> PlayCacheResolvedTemplateValue(value, resolvedValue = value)
            is Boolean -> PlayCacheResolvedTemplateValue(value.toString(), booleanValue = value)
            is Number -> PlayCacheResolvedTemplateValue(value.toString(), resolvedValue = value.toString())
            else -> null
        }
    }

    private fun resolveReference(
        expression: PsiReferenceExpression,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod?,
        anchor: PsiElement
    ): PlayCacheResolvedTemplateValue? {
        val resolved = expression.resolve()
        when (resolved) {
            is PsiParameter -> bindings[resolved.name]?.let { return resolveExpression(it, bindings, method, anchor) }
            is PsiField -> {
                val constant = constantString(resolved)
                if (constant != null) return PlayCacheResolvedTemplateValue(constant, resolvedValue = constant)
                resolved.initializer?.let { return resolveExpression(it, bindings, method, anchor) }
            }
            is PsiLocalVariable -> {
                if (resolved.type.equalsToText("java.lang.StringBuilder")) {
                    evaluateStringBuilder(resolved, method, anchor, bindings)?.let { return it }
                }
                resolved.initializer?.let { return resolveExpression(it, bindings, method, anchor) }
            }
        }
        return null
    }

    private fun resolveMethodCall(
        expression: PsiMethodCallExpression,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod?,
        anchor: PsiElement
    ): PlayCacheResolvedTemplateValue? {
        val text = expression.text
        CONFIG_GET_PROPERTY.matchEntire(text)?.let { match ->
            val configKey = match.groupValues[1]
            val fallback = match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            val service = PlayConfigService.getInstance(expression.project)
            val resolution = service.resolve(configKey)
            val value = resolution.effectiveValue ?: fallback ?: findConfigValueFallback(expression.project, configKey)
            return PlayCacheResolvedTemplateValue(
                displayText = value ?: configKey,
                configurationKey = configKey,
                configurationValue = value,
                resolvedValue = value
            )
        }

        if (expression.methodExpression.referenceName == "toString") {
            val qualifier = expression.methodExpression.qualifierExpression as? PsiReferenceExpression
            val local = qualifier?.resolve() as? PsiLocalVariable
            if (local != null && local.type.equalsToText("java.lang.StringBuilder")) {
                evaluateStringBuilder(local, method, anchor, bindings)?.let { return it }
            }
        }

        val resolvedMethod = expression.resolveMethod() ?: return null
        if (resolvedMethod.containingClass?.qualifiedName?.endsWith(".GMUtils") == true) {
            when (resolvedMethod.name) {
                "getCacheGroovyTemplateDelay" -> {
                    val configKey = "cache.groovytemplate.delay"
                    val value = PlayConfigService.getInstance(expression.project).resolve(configKey).effectiveValue
                        ?: findConfigValueFallback(expression.project, configKey)
                    return PlayCacheResolvedTemplateValue(
                        displayText = value ?: configKey,
                        configurationKey = configKey,
                        configurationValue = value,
                        resolvedValue = value
                    )
                }
                "getCacheGroovyTemplateStatus" -> {
                    val configKey = "cache.groovytemplate.enable"
                    val value = PlayConfigService.getInstance(expression.project).resolve(configKey).effectiveValue
                        ?: findConfigValueFallback(expression.project, configKey)
                    return PlayCacheResolvedTemplateValue(
                        displayText = value ?: configKey,
                        configurationKey = configKey,
                        configurationValue = value,
                        resolvedValue = value
                    )
                }
                "useCachedTemplate" -> {
                    val configKey = "cache.groovytemplate.enable"
                    val value = PlayConfigService.getInstance(expression.project).resolve(configKey).effectiveValue
                        ?: findConfigValueFallback(expression.project, configKey)
                    val enabled = value.equals("on", ignoreCase = true) || value.equals("true", ignoreCase = true)
                    return PlayCacheResolvedTemplateValue(
                        displayText = enabled.toString(),
                        configurationKey = configKey,
                        configurationValue = value,
                        booleanValue = enabled
                    )
                }
            }
        }
        val nestedBindings = bindArguments(resolvedMethod, expression.argumentList.expressions, bindings, method ?: resolvedMethod, anchor)
        resolvedMethod.body?.statements.orEmpty().forEach { statement ->
            if (statement is PsiIfStatement) {
                statement.thenBranch?.let { branch ->
                    PsiTreeUtil.findChildrenOfType(branch, PsiMethodCallExpression::class.java)
                        .firstOrNull { it == expression }?.let { return@forEach }
                }
            }
        }

        resolvedMethod.body?.let { body ->
            val returns = PsiTreeUtil.findChildrenOfType(body, com.intellij.psi.PsiReturnStatement::class.java)
            returns.asSequence()
                .mapNotNull { it.returnValue }
                .forEach { returnExpr ->
                    resolveExpression(returnExpr, nestedBindings, resolvedMethod, returnExpr)?.let { return it }
                }
        }
        return null
    }

    private fun resolvePolyadic(
        expression: PsiPolyadicExpression,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod?,
        anchor: PsiElement
    ): PlayCacheResolvedTemplateValue? {
        if (expression.operationTokenType != JavaTokenType.PLUS) return null
        val parts = expression.operands.mapNotNull { resolveExpression(it, bindings, method, anchor) }
        if (parts.size != expression.operands.size) return null
        val joined = parts.joinToString("") { it.resolvedValue ?: it.configurationValue ?: BUILDER_DYNAMIC_PREFIX }
        return PlayCacheResolvedTemplateValue(joined, resolvedValue = joined)
    }

    private fun resolveBinary(
        expression: PsiBinaryExpression,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod?,
        anchor: PsiElement
    ): PlayCacheResolvedTemplateValue? {
        if (expression.operationTokenType != JavaTokenType.PLUS) return null
        val left = resolveExpression(expression.lOperand, bindings, method, anchor) ?: return null
        val right = expression.rOperand?.let { resolveExpression(it, bindings, method, anchor) } ?: return null
        val value = (left.resolvedValue ?: left.configurationValue ?: BUILDER_DYNAMIC_PREFIX) +
            (right.resolvedValue ?: right.configurationValue ?: BUILDER_DYNAMIC_PREFIX)
        return PlayCacheResolvedTemplateValue(value, resolvedValue = value)
    }

    private fun resolveNewExpression(
        expression: PsiNewExpression,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod?,
        anchor: PsiElement
    ): PlayCacheResolvedTemplateValue? {
        if (!expression.type?.equalsToText("java.lang.StringBuilder").orFalse()) return null
        val first = expression.argumentList?.expressions?.firstOrNull()
        return resolveExpression(first, bindings, method, anchor)
    }

    private fun evaluateStringBuilder(
        local: PsiLocalVariable,
        method: PsiMethod?,
        anchor: PsiElement,
        bindings: Map<String, PsiExpression>
    ): PlayCacheResolvedTemplateValue? {
        val ownerMethod = method ?: PsiTreeUtil.getParentOfType(local, PsiMethod::class.java) ?: return null
        val statements = ownerMethod.body?.statements.orEmpty()
        val anchorStatement = PsiTreeUtil.getParentOfType(anchor, PsiStatement::class.java)
        var builder = resolveExpression(local.initializer, bindings, ownerMethod, local)?.resolvedValue.orEmpty()
        for (statement in statements) {
            if (statement == null) continue
            if (statement == anchorStatement) break
            val expressionStatement = statement as? PsiExpressionStatement ?: continue
            val call = expressionStatement.expression as? PsiMethodCallExpression ?: continue
            if (call.methodExpression.referenceName != "append") continue
            val qualifier = call.methodExpression.qualifierExpression as? PsiReferenceExpression ?: continue
            if (qualifier.resolve() != local) continue
            val arg = call.argumentList.expressions.firstOrNull()
            val resolved = resolveExpression(arg, bindings, ownerMethod, statement)
            builder += resolved?.resolvedValue ?: resolved?.configurationValue ?: BUILDER_DYNAMIC_PREFIX
        }
        return PlayCacheResolvedTemplateValue(builder, resolvedValue = builder)
    }

    private fun evaluateStringExpression(
        expression: PsiExpression?,
        bindings: Map<String, PsiExpression>,
        method: PsiMethod?,
        anchor: PsiElement
    ): String? =
        resolveExpression(expression, bindings, method, anchor)?.resolvedValue
            ?: resolveExpression(expression, bindings, method, anchor)?.configurationValue

    private fun buildResolvedDisplay(
        variableName: String?,
        resolved: PlayCacheResolvedTemplateValue,
        fallback: String
    ): String {
        val base = resolved.resolvedValue ?: resolved.configurationValue ?: fallback
        val viaParts = buildList {
            if (!variableName.isNullOrBlank()) add(variableName)
            if (!resolved.sourceVariable.isNullOrBlank() && resolved.sourceVariable != variableName) add(resolved.sourceVariable)
            if (!resolved.configurationKey.isNullOrBlank()) add("conf ${resolved.configurationKey}")
        }
        return if (viaParts.isEmpty()) base else "$base (via ${viaParts.joinToString(", ")})"
    }

    private fun referencedVariableName(raw: String): String? {
        val unquoted = unquote(raw.trim())
        if (unquoted != null) {
            SINGLE_INTERPOLATION.matchEntire(unquoted)?.let { return it.groupValues[1] }
            SINGLE_INTERPOLATION.find(unquoted)?.let { return it.groupValues[1] }
            if (SIMPLE_IDENTIFIER.matches(unquoted)) return unquoted
        }
        SINGLE_INTERPOLATION.matchEntire(raw)?.let { return it.groupValues[1] }
        SINGLE_INTERPOLATION.find(raw)?.let { return it.groupValues[1] }
        return raw.takeIf { SIMPLE_IDENTIFIER.matches(it) }
    }

    private fun variableNameFromParsed(parsed: Any): String? = when (parsed) {
        is PlayCacheKey.Dynamic -> SINGLE_INTERPOLATION.matchEntire(parsed.expressionText)?.groupValues?.getOrNull(1)
        is PlayCacheTtl.Dynamic -> SINGLE_INTERPOLATION.matchEntire(parsed.expressionText)?.groupValues?.getOrNull(1)
        else -> null
    }

    private fun defaultDisplay(parsed: Any): String = when (parsed) {
        is PlayCacheKey.Static -> parsed.value
        is PlayCacheKey.Pattern -> parsed.value
        is PlayCacheKey.Dynamic -> "dynamic ${parsed.expressionText}"
        PlayCacheKey.Missing -> "missing"
        is PlayCacheTtl.Static -> if (parsed.value.isEmpty()) "no expiration" else parsed.value
        is PlayCacheTtl.Dynamic -> "dynamic ${parsed.expressionText}"
        PlayCacheTtl.Absent -> "no expiration"
        else -> "dynamic"
    }

    private fun constantString(element: PsiElement): String? {
        val helper = JavaPsiFacade.getInstance(element.project).constantEvaluationHelper
        val constant = when (element) {
            is PsiExpression -> helper.computeConstantExpression(element)
            is PsiVariable -> helper.computeConstantExpression(element.initializer)
            else -> null
        }
        return constant as? String
    }

    private fun constantString(variable: PsiVariable): String? = constantString(variable as PsiElement)

    private fun unquote(text: String): String? {
        if (text.length < 2) return null
        val first = text.first()
        val last = text.last()
        return if ((first == '\'' || first == '"') && first == last) {
            text.substring(1, text.length - 1)
        } else null
    }

    private fun findConfigValueFallback(project: Project, key: String): String? =
        PlayConfigService.getInstance(project).keysForLogical(key).firstOrNull()?.value
            ?: run {
                val psiManager = PsiManager.getInstance(project)
                var resolved: String? = null
                FilenameIndex.processFilesByName(
                    "application.conf",
                    true,
                    GlobalSearchScope.projectScope(project)
                ) { virtualFile ->
                    if (!virtualFile.path.replace('\\', '/').endsWith("/conf/application.conf")) {
                        return@processFilesByName true
                    }
                    val configFile = psiManager.findFile(virtualFile) as? PlayConfigFile ?: return@processFilesByName true
                    resolved = configFile.getProperties()
                        .firstOrNull { it.logicalKey == key }
                        ?.valueText
                    resolved == null
                }
                resolved
            }

    private fun Boolean?.orFalse(): Boolean = this == true
}

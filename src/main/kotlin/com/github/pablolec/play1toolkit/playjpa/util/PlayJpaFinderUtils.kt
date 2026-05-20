package com.github.pablolec.play1toolkit.playjpa.util

import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethodCallExpression

private val PLAY_FINDER_METHODS = setOf("find", "findAll", "findById", "all", "count", "delete", "create", "fetch", "first")
private val JPQL_FIELD_PATTERN = Regex("""(\b[a-z]\w*)\s*(?:=|!=|<>|<|>|<=|>=|\blike\b|\bin\b|\bis\b)""", RegexOption.IGNORE_CASE)
private val PLACEHOLDER_PATTERN = Regex("""\?(\d+)?""")

object PlayJpaFinderUtils {

    fun parseByFieldPattern(s: String): List<String> {
        if (!s.startsWith("by", ignoreCase = true)) return emptyList()
        val rest = s.removePrefix("by").removeSuffix("Fetch").removeSuffix("Order")
        // Split on "And" keeping each segment, then uncapitalize
        return rest.split(Regex("(?=[A-Z][a-z])(?<=(?:[a-z]))And|And(?=[A-Z])"))
            .flatMap { splitCamelByAnd(it) }
            .filter { it.isNotBlank() }
            .map { it.replaceFirstChar { c -> c.lowercaseChar() } }
    }

    private fun splitCamelByAnd(s: String): List<String> {
        // "EmailAndActive" → ["Email", "Active"]
        val parts = mutableListOf<String>()
        var current = s
        while (current.contains("And")) {
            val idx = current.indexOf("And")
            if (idx > 0 && idx + 3 < current.length && current[idx + 3].isUpperCase()) {
                parts.add(current.substring(0, idx))
                current = current.substring(idx + 3)
            } else break
        }
        parts.add(current)
        return parts
    }

    fun parseJpqlFields(s: String): List<String> =
        JPQL_FIELD_PATTERN.findAll(s).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()

    fun countQueryPlaceholders(s: String): Int = PLACEHOLDER_PATTERN.findAll(s).count()

    fun resolveFinderModel(call: PsiMethodCallExpression): PsiClass? {
        val methodName = call.methodExpression.referenceName ?: return null
        if (methodName !in PLAY_FINDER_METHODS) return null
        val qualifier = call.methodExpression.qualifierExpression ?: return null
        val qualText = qualifier.text.trim()
        val project = call.project
        val svc = PlayJpaModelService.getInstance(project)
        return svc.findModelByName(qualText)?.psiClass
    }

    fun isFinderCall(call: PsiMethodCallExpression): Boolean =
        call.methodExpression.referenceName in PLAY_FINDER_METHODS

    fun getFinderQueryArg(call: PsiMethodCallExpression): String? {
        val args = call.argumentList.expressions
        if (args.isEmpty()) return null
        val first = args[0]
        return (first as? com.intellij.psi.PsiLiteralExpression)?.value as? String
    }
}

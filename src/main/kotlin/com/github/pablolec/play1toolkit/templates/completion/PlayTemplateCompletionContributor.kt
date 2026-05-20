package com.github.pablolec.play1toolkit.templates.completion

import com.github.pablolec.play1toolkit.render.Play1ViewUtils
import com.github.pablolec.play1toolkit.playjpa.service.PlayJpaModelService
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateVariableResolver
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.xml.XmlText
import com.intellij.util.ProcessingContext

class PlayTemplateCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(XmlText::class.java),
            TagNameCompletionProvider()
        )

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(XmlText::class.java),
            TemplatePathCompletionProvider()
        )

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(XmlText::class.java),
            ReverseRouteCompletionProvider()
        )

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(XmlText::class.java),
            StaticAssetCompletionProvider()
        )

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(XmlText::class.java),
            VariableCompletionProvider()
        )

        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(XmlText::class.java),
            TagParamCompletionProvider()
        )
    }

    private class TagNameCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val element = parameters.position
            if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return
            if (DumbService.isDumb(element.project)) return

            val textBefore = textBeforeCaret(parameters) ?: return
            if (!textBefore.matches(Regex(""".*#\{[\w.]*$"""))) return

            val prefixMatch = Regex("""#\{([\w.]*)$""").find(textBefore) ?: return
            val prefix = prefixMatch.groupValues[1]
            val prefixResult = result.withPrefixMatcher(prefix)

            // Built-in tags
            PlayTemplatePatterns.BUILTIN_TAGS.forEach { tag ->
                val doc = PlayTemplatePatterns.BUILTIN_TAG_DOCS[tag] ?: ""
                prefixResult.addElement(
                    LookupElementBuilder.create(tag)
                        .withTypeText("built-in")
                        .withTailText(if (doc.isNotEmpty()) "  $doc" else "", true)
                )
            }

            // Custom tags
            val svc = PlayTemplateService.getInstance(element.project)
            svc.getAllCustomTags().forEach { tag ->
                val params = if (tag.parameters.isNotEmpty()) tag.parameters.joinToString(", ") else ""
                prefixResult.addElement(
                    LookupElementBuilder.create(tag.qualifiedName)
                        .withTypeText("custom")
                        .withTailText(if (params.isNotEmpty()) "  params: $params" else "", true)
                )
            }
        }
    }

    private class TemplatePathCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val pathPrefixRe = Regex(""".*#\{(?:extends|include)\s+['"]([^'"]*)$""")

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val element = parameters.position
            if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return
            if (DumbService.isDumb(element.project)) return

            val textBefore = textBeforeCaret(parameters) ?: return
            val match = pathPrefixRe.find(textBefore) ?: return
            val prefix = match.groupValues[1]
            val prefixResult = result.withPrefixMatcher(prefix)

            val svc = PlayTemplateService.getInstance(element.project)
            svc.getAllTemplates().forEach { tpl ->
                prefixResult.addElement(
                    LookupElementBuilder.create(tpl.logicalPath)
                        .withTypeText("template")
                )
            }
        }
    }

    private class ReverseRouteCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val routePrefixRe = Regex(""".*@@?\{([A-Z][\w.]*)?$""")

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val element = parameters.position
            if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return
            if (DumbService.isDumb(element.project)) return

            val textBefore = textBeforeCaret(parameters) ?: return
            if (!textBefore.matches(routePrefixRe)) return

            val matchResult = routePrefixRe.find(textBefore) ?: return
            val prefix = matchResult.groupValues[1]
            val prefixResult = result.withPrefixMatcher(prefix)

            val project = element.project
            val scope = GlobalSearchScope.projectScope(project)
            PsiShortNamesCache.getInstance(project).allClassNames.forEach { className ->
                if (className[0].isUpperCase()) {
                    val classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope)
                    classes.forEach { psiClass ->
                        if (Play1ViewUtils.isPlayControllerClass(psiClass)) {
                            psiClass.methods.filter { it.hasModifierProperty("public") && it.name != "<init>" }
                                .forEach { method ->
                                    val routes = Play1ViewUtils.findRoutesForAction(project, className, method.name)
                                    val tail = routes.firstOrNull()
                                        ?.let { "  ${it.getHttpMethod()?.text ?: ""} ${it.getPath() ?: ""}" }
                                        ?: ""
                                    prefixResult.addElement(
                                        LookupElementBuilder.create("$className.${method.name}")
                                            .withTypeText("action")
                                            .withTailText(tail, true)
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    private class StaticAssetCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val assetPrefixRe = Regex(""".*@\{['"]([^'"]*)?$""")

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val element = parameters.position
            if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return
            if (DumbService.isDumb(element.project)) return

            val textBefore = textBeforeCaret(parameters) ?: return
            if (!textBefore.matches(assetPrefixRe)) return

            val match = assetPrefixRe.find(textBefore) ?: return
            val prefix = match.groupValues[1]
            val prefixResult = result.withPrefixMatcher(prefix)

            val project = element.project
            val basePath = project.basePath ?: return
            val publicDir = LocalFileSystem.getInstance().findFileByPath("$basePath/public") ?: return
            collectPublicFiles(publicDir, "/public", prefixResult)
        }

        private fun collectPublicFiles(dir: com.intellij.openapi.vfs.VirtualFile, prefix: String, result: CompletionResultSet) {
            for (child in dir.children) {
                if (child.isDirectory) {
                    collectPublicFiles(child, "$prefix/${child.name}", result)
                } else {
                    result.addElement(
                        LookupElementBuilder.create("$prefix/${child.name}")
                            .withTypeText(child.extension ?: "")
                    )
                }
            }
        }
    }

    private class VariableCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val varPrefixRe = Regex(""".*\$\{(\w*)$""")
        private val memberPrefixRe = Regex(""".*\$\{(\w+)\.(\w*)$""")

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val element = parameters.position
            if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return
            if (DumbService.isDumb(element.project)) return

            val textBefore = textBeforeCaret(parameters) ?: return
            val project = element.project
            val file = element.containingFile ?: return

            val memberMatch = memberPrefixRe.find(textBefore)
            if (memberMatch != null && textBefore.matches(memberPrefixRe)) {
                val variableName = memberMatch.groupValues[1]
                val prefix = memberMatch.groupValues[2]
                val qualifierType = PlayTemplateVariableResolver.getInstance(project).resolveVariableType(file, variableName) ?: return
                val classType = qualifierType as? PsiClassType ?: return
                val psiClass = classType.resolve() ?: return
                val model = PlayJpaModelService.getInstance(project).findModelForClass(psiClass)
                val prefixResult = result.withPrefixMatcher(prefix)
                if (model != null) {
                    (model.fields + (model.idField?.let { listOf(it) } ?: emptyList())).forEach { field ->
                        prefixResult.addElement(
                            LookupElementBuilder.create(field.name)
                                .withTypeText(field.typeText)
                                .withTailText("  model field", true)
                        )
                    }
                    model.relations.forEach { relation ->
                        prefixResult.addElement(
                            LookupElementBuilder.create(relation.fieldName)
                                .withTypeText(relation.targetModel ?: "Object")
                                .withTailText("  relation", true)
                        )
                    }
                    return
                }

                psiClass.allFields
                    .filter { !it.hasModifierProperty(PsiModifier.STATIC) }
                    .forEach { field ->
                        prefixResult.addElement(
                            LookupElementBuilder.create(field.name)
                                .withTypeText(field.type.presentableText)
                                .withTailText("  field", true)
                        )
                    }
                return
            }

            if (!textBefore.matches(varPrefixRe)) return

            val match = varPrefixRe.find(textBefore) ?: return
            val prefix = match.groupValues[1]
            val prefixResult = result.withPrefixMatcher(prefix)
            val variables = PlayTemplateVariableResolver.getInstance(project).resolveVariables(file)

            variables.forEach { varName ->
                prefixResult.addElement(
                    LookupElementBuilder.create(varName)
                        .withTypeText("template variable")
                )
            }
        }
    }

    private class TagParamCompletionProvider : CompletionProvider<CompletionParameters>() {
        private val paramPrefixRe = Regex(""".*#\{(\w[\w.]*)\s+([^}]*)$""")

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val element = parameters.position
            if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return
            if (DumbService.isDumb(element.project)) return

            val textBefore = textBeforeCaret(parameters) ?: return
            val match = paramPrefixRe.find(textBefore) ?: return
            val tagName = match.groupValues[1]
            if (tagName in PlayTemplatePatterns.BUILTIN_TAGS) return

            val svc = PlayTemplateService.getInstance(element.project)
            val tagInfo = svc.findTag(tagName)
                ?: svc.getAllCustomTags().firstOrNull { it.name == tagName }
                ?: return

            val alreadyTyped = match.groupValues[2]
            val lastColon = alreadyTyped.lastIndexOf(',')
            val prefix = alreadyTyped.substring(lastColon + 1).trim()
            val prefixResult = result.withPrefixMatcher(prefix)

            tagInfo.parameters.forEach { param ->
                prefixResult.addElement(
                    LookupElementBuilder.create("$param:")
                        .withTypeText("tag param")
                )
            }
        }
    }

}

private fun textBeforeCaret(parameters: CompletionParameters): String? {
    val offset = parameters.offset
    val text = parameters.originalFile.text ?: return null
    return text.substring(0, minOf(offset, text.length))
}

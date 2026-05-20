package com.github.pablolec.play1toolkit.templates.goto

import com.github.pablolec.play1toolkit.routes.RoutesControllerResolver
import com.github.pablolec.play1toolkit.templates.service.PlayTemplateService
import com.github.pablolec.play1toolkit.templates.util.PlayTemplateFileUtils
import com.github.pablolec.play1toolkit.templates.util.PlayTemplatePatterns
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

class PlayTemplateGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        if (DumbService.isDumb(element.project)) return null
        if (!PlayTemplateFileUtils.isInViewsDirectory(element)) return null

        val file = element.containingFile ?: return null
        val text = file.text ?: return null
        val project = element.project

        val targets = mutableListOf<PsiElement>()

        // #{extends 'path' /}
        PlayTemplatePatterns.TAG_EXTENDS.findAll(text).forEach { match ->
            if (offset in match.range) {
                val path = match.groupValues[1]
                resolveViewsPath(project, path)?.let { targets.add(it) }
                return targets.toTypedArray()
            }
        }

        // #{include 'path' /}
        PlayTemplatePatterns.TAG_INCLUDE.findAll(text).forEach { match ->
            if (offset in match.range) {
                val path = match.groupValues[1]
                resolveViewsPath(project, path)?.let { targets.add(it) }
                return targets.toTypedArray()
            }
        }

        // @{Controller.action(args)} or @@{...}
        PlayTemplatePatterns.REVERSE_ROUTE.findAll(text).forEach { match ->
            if (offset in match.range) {
                val fullRef = match.groupValues[1]
                val dotIdx = fullRef.lastIndexOf('.')
                if (dotIdx > 0) {
                    val controllerName = fullRef.substring(0, dotIdx)
                    val actionName = fullRef.substring(dotIdx + 1)
                    RoutesControllerResolver.resolveMethod(project, controllerName, actionName)
                        ?.let { targets.add(it) }
                } else {
                    RoutesControllerResolver.resolveClass(project, fullRef)
                        ?.let { targets.add(it) }
                }
                return targets.toTypedArray()
            }
        }

        // @{'/public/path'} — static asset
        PlayTemplatePatterns.STATIC_ASSET.findAll(text).forEach { match ->
            if (offset in match.range) {
                val path = match.groupValues[1]
                val vf = PlayTemplateFileUtils.resolvePublicAsset(project, path)
                if (vf != null) {
                    PsiManager.getInstance(project).findFile(vf)?.let { targets.add(it) }
                }
                return targets.toTypedArray()
            }
        }

        // #{tagname ...} — custom tag navigation (non built-in)
        PlayTemplatePatterns.TAG_NAME_AT.findAll(text).forEach { match ->
            if (offset in match.range) {
                val tagName = match.groupValues[1]
                if (tagName !in PlayTemplatePatterns.BUILTIN_TAGS) {
                    val svc = PlayTemplateService.getInstance(project)
                    val tagInfo = svc.findTag(tagName)
                    if (tagInfo != null) {
                        PsiManager.getInstance(project).findFile(tagInfo.virtualFile)
                            ?.let { targets.add(it) }
                    } else {
                        // Try simple name match without namespace
                        svc.getAllCustomTags().firstOrNull { it.name == tagName }?.let { info ->
                            PsiManager.getInstance(project).findFile(info.virtualFile)
                                ?.let { targets.add(it) }
                        }
                    }
                }
                return targets.toTypedArray()
            }
        }

        return null
    }

    private fun resolveViewsPath(project: com.intellij.openapi.project.Project, path: String): PsiElement? {
        val vf = PlayTemplateFileUtils.resolveTemplatePath(project, path) ?: return null
        return PsiManager.getInstance(project).findFile(vf)
    }
}

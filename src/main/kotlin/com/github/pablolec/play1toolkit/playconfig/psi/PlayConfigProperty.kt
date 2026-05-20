package com.github.pablolec.play1toolkit.playconfig.psi

import com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigTokenTypes
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.IncorrectOperationException

/**
 * PSI element for a single Play 1 configuration property.
 *
 * For `%docker.db.url=value`:  profile="docker", logicalKey="db.url", rawKey="%docker.db.url"
 * For `db.url=value`:          profile=null,     logicalKey="db.url", rawKey="db.url"
 *
 * getName() returns the logicalKey (used by find usages, rename, etc.)
 */
class PlayConfigProperty(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    val rawKey: String get() = keyNode?.text?.trim() ?: ""

    val profile: String? get() {
        val raw = rawKey
        if (!raw.startsWith("%")) return null
        val dotIdx = raw.indexOf('.')
        if (dotIdx <= 1) return null
        return raw.substring(1, dotIdx)
    }

    val logicalKey: String get() {
        val raw = rawKey
        if (!raw.startsWith("%")) return raw
        val dotIdx = raw.indexOf('.')
        if (dotIdx < 0 || dotIdx >= raw.length - 1) return raw
        return raw.substring(dotIdx + 1)
    }

    val valueText: String get() {
        // Traverse PROPERTY's children after the SEPARATOR to collect VALUE + ENV_PLACEHOLDER tokens.
        return buildString {
            var child: ASTNode? = node.firstChildNode
            var pastSeparator = false
            while (child != null) {
                when {
                    child.elementType == PlayConfigTokenTypes.SEPARATOR -> pastSeparator = true
                    child.elementType == PlayConfigTokenTypes.NEWLINE -> break
                    pastSeparator && (child.elementType == PlayConfigTokenTypes.VALUE ||
                        child.elementType == PlayConfigTokenTypes.ENV_PLACEHOLDER) ->
                        append(child.text)
                }
                child = child.treeNext
            }
        }.trim()
    }

    private val keyNode: ASTNode? get() = node.findChildByType(PlayConfigTokenTypes.KEY)

    override fun getNameIdentifier(): PsiElement? = keyNode?.psi

    override fun getName(): String = logicalKey

    override fun setName(name: String): PsiElement {
        val keyNode = this.keyNode ?: throw IncorrectOperationException("No key node")
        val newRaw = if (profile != null) "%$profile.$name" else name
        val newKeyLeaf = createKeyLeaf(project, newRaw)
        node.replaceChild(keyNode, newKeyLeaf.node)
        return this
    }

    private fun createKeyLeaf(project: com.intellij.openapi.project.Project, rawKey: String): LeafPsiElement {
        val factory = com.intellij.psi.PsiFileFactory.getInstance(project)
        val file = factory.createFileFromText(
            "dummy.conf",
            com.github.pablolec.play1toolkit.playconfig.lang.PlayConfigFileType,
            "$rawKey=dummy"
        ) as? PlayConfigFile ?: throw IncorrectOperationException("Cannot create PlayConfig element")
        val prop = file.getProperties().firstOrNull()
            ?: throw IncorrectOperationException("Cannot create PlayConfig key element")
        return prop.nameIdentifier as? LeafPsiElement
            ?: throw IncorrectOperationException("Name identifier is not a leaf")
    }

    override fun toString(): String = "PlayConfigProperty($rawKey)"
}

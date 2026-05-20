package com.github.pablolec.play1toolkit.playmessages.psi

import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesFileType
import com.github.pablolec.play1toolkit.playmessages.lang.PlayMessagesTokenTypes
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.IncorrectOperationException

/**
 * PSI element for a single Play 1 i18n message property.
 *
 * For `hello=Hello World`:     key="hello", value="Hello World", locale from containing file
 * For `greeting.user=Hi %s`:   key="greeting.user", value="Hi %s" (valueText includes placeholder text)
 *
 * getName() returns the key (used by find usages, rename, etc.)
 */
class PlayMessagesProperty(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    val key: String get() = keyNode?.text?.trim() ?: ""

    val valueText: String get() {
        return buildString {
            var child: ASTNode? = node.firstChildNode
            var pastSeparator = false
            while (child != null) {
                when {
                    child.elementType == PlayMessagesTokenTypes.SEPARATOR -> pastSeparator = true
                    child.elementType == PlayMessagesTokenTypes.NEWLINE   -> break
                    pastSeparator && (child.elementType == PlayMessagesTokenTypes.VALUE ||
                        child.elementType == PlayMessagesTokenTypes.PLACEHOLDER) ->
                        append(child.text)
                }
                child = child.treeNext
            }
        }.trim()
    }

    val locale: String? get() = (containingFile as? PlayMessagesFile)?.locale

    private val keyNode: ASTNode? get() = node.findChildByType(PlayMessagesTokenTypes.KEY)

    override fun getNameIdentifier(): PsiElement? = keyNode?.psi

    override fun getName(): String = key

    override fun setName(name: String): PsiElement {
        val kn = keyNode ?: throw IncorrectOperationException("No key node")
        val newKeyLeaf = createKeyLeaf(project, name)
        node.replaceChild(kn, newKeyLeaf.node)
        return this
    }

    private fun createKeyLeaf(project: Project, key: String): LeafPsiElement {
        val factory = PsiFileFactory.getInstance(project)
        val file = factory.createFileFromText("dummy", PlayMessagesFileType, "$key=dummy") as? PlayMessagesFile
            ?: throw IncorrectOperationException("Cannot create PlayMessages element")
        val prop = file.getProperties().firstOrNull()
            ?: throw IncorrectOperationException("Cannot create PlayMessages key element")
        return prop.nameIdentifier as? LeafPsiElement
            ?: throw IncorrectOperationException("Name identifier is not a leaf")
    }

    override fun toString(): String = "PlayMessagesProperty($key)"
}

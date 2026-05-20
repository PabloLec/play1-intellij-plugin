package com.github.pablolec.play1toolkit.playjpa.util

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

private val FIXTURE_KEY_PATTERN = Regex("""^(\w+)\((\w+)\)$""")

object PlayYamlFixtureUtils {

    fun isFixtureFile(vf: VirtualFile): Boolean {
        val ext = vf.extension?.lowercase() ?: return false
        if (ext != "yml" && ext != "yaml") return false
        val path = vf.path
        return path.contains("/conf/") || path.contains("/test/")
    }

    fun looksLikeFixtureFile(file: YAMLFile): Boolean {
        val mapping = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return false
        return mapping.keyValues.any { kv ->
            FIXTURE_KEY_PATTERN.matches(kv.keyText.trim())
        }
    }

    fun parseFixtureKey(keyText: String): Pair<String, String>? {
        val m = FIXTURE_KEY_PATTERN.matchEntire(keyText.trim()) ?: return null
        return Pair(m.groupValues[1], m.groupValues[2])
    }

    fun getAllAliasesForModel(yamlFile: YAMLFile, modelName: String): List<String> {
        val mapping = yamlFile.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return emptyList()
        return mapping.keyValues.mapNotNull { kv ->
            val (cls, alias) = parseFixtureKey(kv.keyText) ?: return@mapNotNull null
            if (cls == modelName) alias else null
        }
    }

    fun getTopLevelKeyValues(file: YAMLFile): List<YAMLKeyValue> {
        val mapping = file.documents.firstOrNull()?.topLevelValue as? YAMLMapping ?: return emptyList()
        return mapping.keyValues.toList()
    }

    fun getModelNameFromKey(kv: YAMLKeyValue): String? = parseFixtureKey(kv.keyText)?.first

    fun getAliasFromKey(kv: YAMLKeyValue): String? = parseFixtureKey(kv.keyText)?.second
}

package com.github.pablolec.play1toolkit.playconfig.service

import com.github.pablolec.play1toolkit.playconfig.model.PlayConfigKey
import com.github.pablolec.play1toolkit.playconfig.model.PlayConfigResolution
import com.github.pablolec.play1toolkit.playconfig.model.PlayConfigValueSource
import com.github.pablolec.play1toolkit.playconfig.psi.PlayConfigFile
import com.github.pablolec.play1toolkit.playconfig.settings.PlayConfigProjectSettings
import com.github.pablolec.play1toolkit.run.Play1ApplicationRunConfiguration
import com.intellij.execution.RunManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class PlayConfigService(private val project: Project) {

    private val log = logger<PlayConfigService>()

    companion object {
        fun getInstance(project: Project): PlayConfigService =
            project.getService(PlayConfigService::class.java)
    }

    fun getConfigFile(): PlayConfigFile? {
        if (DumbService.isDumb(project)) return null
        val baseDir = project.basePathAsVirtualFile() ?: return null
        val confFile = baseDir.findFileByRelativePath("conf/application.conf") ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(confFile) ?: return null
        return psiFile as? PlayConfigFile
    }

    fun allKeys(): List<PlayConfigKey> {
        val configFile = getConfigFile() ?: return emptyList()
        return CachedValuesManager.getCachedValue(configFile) {
            CachedValueProvider.Result.create(
                buildKeys(configFile),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }

    private fun buildKeys(file: PlayConfigFile): List<PlayConfigKey> {
        val doc = PsiDocumentManager.getInstance(project).getDocument(file)
        return file.getProperties().map { prop ->
            val line = if (doc != null) doc.getLineNumber(prop.textOffset) + 1 else 0
            PlayConfigKey(
                rawKey = prop.rawKey,
                logicalKey = prop.logicalKey,
                profile = prop.profile,
                value = prop.valueText,
                property = prop,
                lineNumber = line
            )
        }
    }

    fun keysForLogical(logicalKey: String): List<PlayConfigKey> =
        allKeys().filter { it.logicalKey == logicalKey }

    fun availableProfiles(): List<String> =
        allKeys().mapNotNull { it.profile }.distinct().sorted()

    fun resolve(logicalKey: String, activeProfile: String? = resolveActiveProfile()): PlayConfigResolution {
        val keys = keysForLogical(logicalKey)
        val defaultValue = keys.firstOrNull { it.profile == null }
        val profileValue = if (activeProfile != null) keys.firstOrNull { it.profile == activeProfile } else null
        val effective = profileValue ?: defaultValue
        val envVars = resolveEnvVarsFromRunConfig()

        val (resolvedValue, source) = when {
            effective == null -> null to PlayConfigValueSource.UNKNOWN
            else -> resolveValue(effective.value, envVars)
        }

        val unresolved = effective?.value?.let { extractEnvVarNames(it) }
            ?.filter { it !in envVars && System.getenv(it) == null }
            ?: emptyList()

        return PlayConfigResolution(
            logicalKey = logicalKey,
            activeProfile = activeProfile,
            defaultValue = defaultValue,
            profileValue = profileValue,
            effectiveValue = resolvedValue,
            valueSource = source,
            unresolvedEnvironmentVariables = unresolved
        )
    }

    fun resolveActiveProfile(): String? {
        val runConfig = getSelectedPlayRunConfig()
        if (runConfig != null && runConfig.playId.isNotBlank()) return runConfig.playId
        val settings = PlayConfigProjectSettings.getInstance(project)
        return settings.activeFrameworkId.takeIf { it.isNotBlank() }
    }

    private fun getSelectedPlayRunConfig(): Play1ApplicationRunConfiguration? = try {
        RunManager.getInstance(project).selectedConfiguration?.configuration as? Play1ApplicationRunConfiguration
    } catch (e: Exception) {
        null
    }

    fun resolveEnvVarsFromRunConfig(): Map<String, String> =
        getSelectedPlayRunConfig()?.envVars ?: emptyMap()

    private fun resolveValue(raw: String, envVars: Map<String, String>): Pair<String?, PlayConfigValueSource> {
        if (!raw.contains("\${")) return raw to PlayConfigValueSource.DEFAULT

        var result = raw
        var source = PlayConfigValueSource.DEFAULT

        for ((name, value) in envVars) {
            if (result.contains("\${$name}")) {
                result = result.replace("\${$name}", value)
                source = PlayConfigValueSource.RUN_CONFIGURATION_ENVIRONMENT
            }
        }

        for (name in extractEnvVarNames(result)) {
            val sysVal = System.getenv(name)
            if (sysVal != null) {
                result = result.replace("\${$name}", sysVal)
                if (source == PlayConfigValueSource.DEFAULT) source = PlayConfigValueSource.SYSTEM_ENVIRONMENT
            }
        }

        return result to source
    }

    fun extractEnvVarNames(value: String): List<String> =
        Regex("""\$\{([^}]+)}""").findAll(value).map { it.groupValues[1] }.toList()

    fun displayValue(resolution: PlayConfigResolution): String {
        val value = resolution.effectiveValue ?: return "unresolved config key"
        return if (value.length > 40) value.take(37) + "..." else value
    }

    private fun Project.basePathAsVirtualFile() =
        basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
}

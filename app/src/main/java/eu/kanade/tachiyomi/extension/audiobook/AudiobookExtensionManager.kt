package eu.kanade.tachiyomi.extension.audiobook

import android.content.Context
import android.graphics.drawable.Drawable
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.audiobook.api.AudiobookExtensionGithubApi
import eu.kanade.tachiyomi.extension.audiobook.model.AudiobookExtension
import eu.kanade.tachiyomi.extension.audiobook.model.AudiobookLoadResult
import eu.kanade.tachiyomi.extension.audiobook.util.AudiobookExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.audiobook.util.AudiobookExtensionInstaller
import eu.kanade.tachiyomi.extension.audiobook.util.AudiobookExtensionLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import logcat.LogPriority
import tachiyomi.core.i18n.stringResource
import tachiyomi.core.preference.plusAssign
import tachiyomi.core.util.lang.launchNow
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.source.audiobook.model.StubAudiobookSource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale

/**
 * The manager of audiobook extensions installed as another apk which extend the available sources. It handles
 * the retrieval of remotely available audiobook extensions as well as installing, updating and removing them.
 * To avoid malicious distribution, every audiobook extension must be signed and it will only be loaded if its
 * signature is trusted, otherwise the user will be prompted with a warning to trust it before being
 * loaded.
 *
 * @param context The application context.
 * @param preferences The application preferences.
 */
class AudiobookExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
) {

    var isInitialized = false
        private set

    /**
     * API where all the available audiobook extensions can be found.
     */
    private val api = AudiobookExtensionGithubApi()

    /**
     * The installer which installs, updates and uninstalls the audiobook extensions.
     */
    private val installer by lazy { AudiobookExtensionInstaller(context) }

    private val iconMap = mutableMapOf<String, Drawable>()

    private val _installedAudiobookExtensionsFlow = MutableStateFlow(
        emptyList<AudiobookExtension.Installed>(),
    )
    val installedExtensionsFlow = _installedAudiobookExtensionsFlow.asStateFlow()

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages().isSet()

    fun getAppIconForSource(sourceId: Long): Drawable? {
        val pkgName = _installedAudiobookExtensionsFlow.value.find { ext -> ext.sources.any { it.id == sourceId } }?.pkgName
        if (pkgName != null) {
            return iconMap[pkgName] ?: iconMap.getOrPut(pkgName) {
                AudiobookExtensionLoader.getAudiobookExtensionPackageInfoFromPkgName(context, pkgName)!!.applicationInfo
                    .loadIcon(context.packageManager)
            }
        }
        return null
    }

    private val _availableAudiobookExtensionsFlow = MutableStateFlow(
        emptyList<AudiobookExtension.Available>(),
    )
    val availableExtensionsFlow = _availableAudiobookExtensionsFlow.asStateFlow()

    private var availableAudiobookExtensionsSourcesData: Map<Long, StubAudiobookSource> = emptyMap()

    private fun setupAvailableAudiobookExtensionsSourcesDataMap(
        audiobookextensions: List<AudiobookExtension.Available>,
    ) {
        if (audiobookextensions.isEmpty()) return
        availableAudiobookExtensionsSourcesData = audiobookextensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableAudiobookExtensionsSourcesData[id]

    private val _untrustedAudiobookExtensionsFlow = MutableStateFlow(
        emptyList<AudiobookExtension.Untrusted>(),
    )
    val untrustedExtensionsFlow = _untrustedAudiobookExtensionsFlow.asStateFlow()

    init {
        initAudiobookExtensions()
        AudiobookExtensionInstallReceiver(AudiobookInstallationListener()).register(context)
    }

    /**
     * Loads and registers the installed audiobookextensions.
     */
    private fun initAudiobookExtensions() {
        val audiobookextensions = AudiobookExtensionLoader.loadExtensions(context)

        _installedAudiobookExtensionsFlow.value = audiobookextensions
            .filterIsInstance<AudiobookLoadResult.Success>()
            .map { it.extension }

        _untrustedAudiobookExtensionsFlow.value = audiobookextensions
            .filterIsInstance<AudiobookLoadResult.Untrusted>()
            .map { it.extension }

        isInitialized = true
    }

    /**
     * Finds the available audiobook extensions in the [api] and updates [availableExtensions].
     */
    suspend fun findAvailableExtensions() {
        val extensions: List<AudiobookExtension.Available> = try {
            api.findExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withUIContext { context.stringResource(MR.strings.extension_api_error) }
            emptyList()
        }

        enableAdditionalSubLanguages(extensions)

        _availableAudiobookExtensionsFlow.value = extensions
        updatedInstalledAudiobookExtensionsStatuses(extensions)
        setupAvailableAudiobookExtensionsSourcesDataMap(extensions)
    }

    /**
     * Enables the additional sub-languages in the app first run. This addresses
     * the issue where users still need to enable some specific languages even when
     * the device language is inside that major group. As an example, if a user
     * has a zh device language, the app will also enable zh-Hans and zh-Hant.
     *
     * If the user have already changed the enabledLanguages preference value once,
     * the new languages will not be added to respect the user enabled choices.
     */
    private fun enableAdditionalSubLanguages(audiobookextensions: List<AudiobookExtension.Available>) {
        if (subLanguagesEnabledOnFirstRun || audiobookextensions.isEmpty()) {
            return
        }

        // Use the source lang as some aren't present on the audiobookextension level.
        val availableLanguages = audiobookextensions
            .flatMap(AudiobookExtension.Available::sources)
            .distinctBy(AudiobookExtension.Available.AudiobookSource::lang)
            .map(AudiobookExtension.Available.AudiobookSource::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages().defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages().set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    /**
     * Sets the update field of the installed audiobookextensions with the given [availableAudiobookExtensions].
     *
     * @param availableAudiobookExtensions The list of audiobookextensions given by the [api].
     */
    private fun updatedInstalledAudiobookExtensionsStatuses(
        availableAudiobookExtensions: List<AudiobookExtension.Available>,
    ) {
        if (availableAudiobookExtensions.isEmpty()) {
            preferences.audiobookExtensionUpdatesCount().set(0)
            return
        }

        val mutInstalledAudiobookExtensions = _installedAudiobookExtensionsFlow.value.toMutableList()
        var changed = false

        for ((index, installedExt) in mutInstalledAudiobookExtensions.withIndex()) {
            val pkgName = installedExt.pkgName
            val availableExt = availableAudiobookExtensions.find { it.pkgName == pkgName }

            if (!installedExt.isUnofficial && availableExt == null && !installedExt.isObsolete) {
                mutInstalledAudiobookExtensions[index] = installedExt.copy(isObsolete = true)
                changed = true
            } else if (availableExt != null) {
                val hasUpdate = installedExt.updateExists(availableExt)

                if (installedExt.hasUpdate != hasUpdate) {
                    mutInstalledAudiobookExtensions[index] = installedExt.copy(hasUpdate = hasUpdate)
                    changed = true
                }
            }
        }
        if (changed) {
            _installedAudiobookExtensionsFlow.value = mutInstalledAudiobookExtensions
        }
        updatePendingUpdatesCount()
    }

    /**
     * Returns a flow of the installation process for the given audiobook extension. It will complete
     * once the audiobook extension is installed or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The audiobook extension to be installed.
     */
    fun installExtension(extension: AudiobookExtension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(api.getApkUrl(extension), extension)
    }

    /**
     * Returns a flow of the installation process for the given audiobook extension. It will complete
     * once the audiobook extension is updated or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The audiobook extension to be updated.
     */
    fun updateExtension(extension: AudiobookExtension.Installed): Flow<InstallStep> {
        val availableExt = _availableAudiobookExtensionsFlow.value.find { it.pkgName == extension.pkgName }
            ?: return emptyFlow()
        return installExtension(availableExt)
    }

    fun cancelInstallUpdateExtension(extension: AudiobookExtension) {
        installer.cancelInstall(extension.pkgName)
    }

    /**
     * Sets to "installing" status of an audiobook extension installation.
     *
     * @param downloadId The id of the download.
     */
    fun setInstalling(downloadId: Long) {
        installer.updateInstallStep(downloadId, InstallStep.Installing)
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        installer.updateInstallStep(downloadId, step)
    }

    /**
     * Uninstalls the audiobook extension that matches the given package name.
     *
     * @param extension The extension to uninstall.
     */
    fun uninstallExtension(extension: AudiobookExtension) {
        installer.uninstallApk(extension.pkgName)
    }

    /**
     * Adds the given signature to the list of trusted signatures. It also loads in background the
     * audiobook extensions that match this signature.
     *
     * @param signature The signature to whitelist.
     */
    fun trustSignature(signature: String) {
        val untrustedSignatures = _untrustedAudiobookExtensionsFlow.value.map { it.signatureHash }.toSet()
        if (signature !in untrustedSignatures) return

        AudiobookExtensionLoader.trustedSignatures += signature
        preferences.trustedSignatures() += signature

        val nowTrustedAudiobookExtensions = _untrustedAudiobookExtensionsFlow.value.filter { it.signatureHash == signature }
        _untrustedAudiobookExtensionsFlow.value -= nowTrustedAudiobookExtensions

        launchNow {
            nowTrustedAudiobookExtensions
                .map { audiobookextension ->
                    async {
                        AudiobookExtensionLoader.loadExtensionFromPkgName(
                            context,
                            audiobookextension.pkgName,
                        )
                    }.await()
                }
                .filterIsInstance<AudiobookLoadResult.Success>()
                .forEach { registerNewExtension(it.extension) }
        }
    }

    /**
     * Registers the given audiobook extension in this and the source managers.
     *
     * @param extension The audiobook extension to be registered.
     */
    private fun registerNewExtension(extension: AudiobookExtension.Installed) {
        _installedAudiobookExtensionsFlow.value += extension
    }

    /**
     * Registers the given updated audiobook extension in this and the source managers previously removing
     * the outdated ones.
     *
     * @param extension The audiobook extension to be registered.
     */
    private fun registerUpdatedExtension(extension: AudiobookExtension.Installed) {
        val mutInstalledAudiobookExtensions = _installedAudiobookExtensionsFlow.value.toMutableList()
        val oldAudiobookExtension = mutInstalledAudiobookExtensions.find { it.pkgName == extension.pkgName }
        if (oldAudiobookExtension != null) {
            mutInstalledAudiobookExtensions -= oldAudiobookExtension
        }
        mutInstalledAudiobookExtensions += extension
        _installedAudiobookExtensionsFlow.value = mutInstalledAudiobookExtensions
    }

    /**
     * Unregisters the audiobookextension in this and the source managers given its package name. Note this
     * method is called for every uninstalled application in the system.
     *
     * @param pkgName The package name of the uninstalled application.
     */
    private fun unregisterAudiobookExtension(pkgName: String) {
        val installedAudiobookExtension = _installedAudiobookExtensionsFlow.value.find { it.pkgName == pkgName }
        if (installedAudiobookExtension != null) {
            _installedAudiobookExtensionsFlow.value -= installedAudiobookExtension
        }
        val untrustedAudiobookExtension = _untrustedAudiobookExtensionsFlow.value.find { it.pkgName == pkgName }
        if (untrustedAudiobookExtension != null) {
            _untrustedAudiobookExtensionsFlow.value -= untrustedAudiobookExtension
        }
    }

    /**
     * Listener which receives events of the audiobook extensions being installed, updated or removed.
     */
    private inner class AudiobookInstallationListener : AudiobookExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: AudiobookExtension.Installed) {
            registerNewExtension(extension.withUpdateCheck())
            updatePendingUpdatesCount()
        }

        override fun onExtensionUpdated(extension: AudiobookExtension.Installed) {
            registerUpdatedExtension(extension.withUpdateCheck())
            updatePendingUpdatesCount()
        }

        override fun onExtensionUntrusted(extension: AudiobookExtension.Untrusted) {
            _untrustedAudiobookExtensionsFlow.value += extension
        }

        override fun onPackageUninstalled(pkgName: String) {
            AudiobookExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterAudiobookExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    /**
     * AudiobookExtension method to set the update field of an installed audiobook extension.
     */
    private fun AudiobookExtension.Installed.withUpdateCheck(): AudiobookExtension.Installed {
        return if (updateExists()) {
            copy(hasUpdate = true)
        } else {
            this
        }
    }

    private fun AudiobookExtension.Installed.updateExists(
        availableAudiobookExtension: AudiobookExtension.Available? = null,
    ): Boolean {
        val availableExt = availableAudiobookExtension ?: _availableAudiobookExtensionsFlow.value.find { it.pkgName == pkgName }
        if (isUnofficial || availableExt == null) return false

        return (availableExt.versionCode > versionCode || availableExt.libVersion > libVersion)
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = _installedAudiobookExtensionsFlow.value.count { it.hasUpdate }
        preferences.audiobookExtensionUpdatesCount().set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            ExtensionUpdateNotifier(context).dismiss()
        }
    }
}

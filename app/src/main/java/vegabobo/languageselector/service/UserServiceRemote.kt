package vegabobo.languageselector.service

import android.os.LocaleList
import vegabobo.languageselector.core.contract.LocaleRemote
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LocaleRemote] implementation on top of the privileged [ServiceManager].
 *
 * Every call goes through [ServiceManager.withService], so binder calls are serialised,
 * time-limited and never run on the main thread. Package lookups are batched (see
 * [PACKAGE_QUERY_BATCH_SIZE]) through the dedicated AIDL method.
 */
@Singleton
class UserServiceRemote @Inject constructor(
    private val serviceManager: ServiceManager,
) : LocaleRemote {

    override suspend fun getUid(): Int = serviceManager.withService { service -> service.uid }

    override suspend fun setApplicationLocales(packageName: String, languageTags: List<String>) {
        val locales = localeListOf(languageTags)
        serviceManager.withService { service -> service.setApplicationLocales(packageName, locales) }
    }

    override suspend fun getApplicationLocalesTags(
        packageNames: List<String>,
    ): Map<String, List<String>> {
        if (packageNames.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, List<String>>(packageNames.size)
        for (batch in chunkPackages(packageNames)) {
            val remoteTags = serviceManager.withService { service ->
                service.getApplicationLocalesTags(batch.toTypedArray())
            }
            batch.forEachIndexed { index, packageName ->
                result[packageName] = parseTags(remoteTags?.getOrNull(index))
            }
        }
        return result
    }

    override suspend fun getSystemLocales(): List<String> {
        val locales = serviceManager.withService { service -> service.systemLocales }
        return localeTags(locales)
    }

    override suspend fun forceStopPackage(packageName: String) {
        serviceManager.withService { service -> service.forceStopPackage(packageName) }
    }

    override suspend fun firstRunningTaskPackage(): String =
        serviceManager.withService { service -> service.firstRunningTaskPackage ?: "" }

    private fun localeListOf(languageTags: List<String>): LocaleList {
        val normalized = normalizeLanguageTags(languageTags)
        if (normalized.isEmpty()) return LocaleList.getEmptyLocaleList()
        return LocaleList(*normalized.map { tag -> Locale.forLanguageTag(tag) }.toTypedArray())
    }

    private fun localeTags(locales: LocaleList?): List<String> {
        if (locales == null || locales.isEmpty) return emptyList()
        val tags = ArrayList<String>(locales.size())
        for (index in 0 until locales.size()) {
            tags.add(locales.get(index).toLanguageTag())
        }
        return normalizeLanguageTags(tags)
    }
}

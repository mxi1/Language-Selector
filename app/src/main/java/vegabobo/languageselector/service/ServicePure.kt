package vegabobo.languageselector.service

/**
 * Pure helpers shared by the local and remote side of [UserService].
 *
 * They are deliberately free of Android dependencies so they can be covered by plain
 * JVM unit tests (see `app/src/test/java/vegabobo/languageselector/service`).
 */

/** How many packages are sent to the privileged service in a single binder call. */
internal const val PACKAGE_QUERY_BATCH_SIZE = 50

/**
 * Splits [packageNames] into batches of at most [batchSize] entries, preserving order.
 * An empty input yields an empty list. A non-positive [batchSize] falls back to
 * [PACKAGE_QUERY_BATCH_SIZE].
 */
internal fun chunkPackages(
    packageNames: List<String>,
    batchSize: Int = PACKAGE_QUERY_BATCH_SIZE,
): List<List<String>> {
    if (packageNames.isEmpty()) return emptyList()
    val size = if (batchSize > 0) batchSize else PACKAGE_QUERY_BATCH_SIZE
    return packageNames.chunked(size)
}

/**
 * Parses the remote representation of an app locale list: language tags joined with `;`.
 * Blank entries are dropped and duplicates are removed. `null`/blank input means the
 * app follows the system default locales.
 */
internal fun parseTags(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** Serializes language tags into the remote `;` separated representation. */
internal fun formatTags(languageTags: List<String>): String =
    normalizeLanguageTags(languageTags).joinToString(";")

/**
 * Trims, drops blank and duplicates tags while keeping the priority order. The result is
 * safe to feed into `LocaleList`.
 */
internal fun normalizeLanguageTags(languageTags: List<String>): List<String> {
    if (languageTags.isEmpty()) return emptyList()
    return languageTags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

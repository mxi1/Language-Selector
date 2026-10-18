package vegabobo.languageselector.data

import vegabobo.languageselector.core.model.AppFilter
import vegabobo.languageselector.core.model.AppSummary
import java.text.Collator
import java.util.Locale

/**
 * Applies [filter] to this list, keeps only apps matching [query] (label or
 * package name, case-insensitive, surrounding whitespace ignored) and sorts the
 * result.
 *
 * Ordering keeps the legacy behaviour: modified apps first, then by label using
 * a [Collator] so sorting follows the user's language; the package name is the
 * final tie breaker to keep the order stable.
 */
fun List<AppSummary>.filterAndSort(
    filter: AppFilter,
    query: String,
    modifiedPackages: Set<String>,
): List<AppSummary> {
    val needle = query.trim()
    val filtered = asSequence()
        .filter { app ->
            val isModified = app.packageName in modifiedPackages
            if (filter.onlyModified && !isModified) return@filter false
            if (!filter.showSystem && app.isSystem && !isModified) return@filter false
            if (needle.isEmpty()) return@filter true
            app.label.contains(needle, ignoreCase = true) ||
                app.packageName.contains(needle, ignoreCase = true)
        }
        .toList()

    val collator = Collator.getInstance(Locale.getDefault())
    return filtered.sortedWith(
        compareByDescending<AppSummary> { it.packageName in modifiedPackages }
            .thenComparator { left, right -> collator.compare(left.label, right.label) }
            .thenBy { it.packageName }
    )
}

/**
 * Groups [apps] into letter sections for the sticky-header list.
 *
 * Apps keep their incoming order inside a section; sections are ordered
 * alphabetically by first character, and apps whose label starts with a
 * non-letter (or a blank label) fall into the `'#'` section, which is placed
 * last. No app is dropped.
 */
fun indexByLetter(apps: List<AppSummary>): List<Pair<Char, List<AppSummary>>> {
    if (apps.isEmpty()) return emptyList()
    val sections = LinkedHashMap<Char, MutableList<AppSummary>>()
    for (app in apps) {
        val first = app.label.trim().firstOrNull()
        val letter = first?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        sections.getOrPut(letter) { mutableListOf() }.add(app)
    }
    return sections
        .map { (letter, group) -> letter to group.toList() }
        .sortedWith(
            compareBy<Pair<Char, List<AppSummary>>> { it.first == '#' }.thenBy { it.first }
        )
}

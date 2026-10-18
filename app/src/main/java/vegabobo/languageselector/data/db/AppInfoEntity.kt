package vegabobo.languageselector.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Historical record of an app the user has opened from the list.
 *
 * The schema is intentionally byte-for-byte compatible with the pre-rewrite
 * database (`app-info-db`, table `appinfoentity`, version 1) so that history
 * survives an in-place upgrade:
 * - `pkg` is the primary key,
 * - `name` is the app label (kept for legacy rows/compat; new rows store the
 *   package name because the history contract only hands us a package name),
 * - `last_selected` is the epoch millis of the last selection, `NULL` when the
 *   row is not part of the recent history anymore.
 */
@Entity(tableName = "appinfoentity")
data class AppInfoEntity(
    @PrimaryKey
    @ColumnInfo(name = "pkg")
    val pkg: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "last_selected")
    val lastSelected: Long? = null,
)

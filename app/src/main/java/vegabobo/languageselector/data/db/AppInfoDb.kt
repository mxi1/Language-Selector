package vegabobo.languageselector.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app history database.
 *
 * The pre-rewrite app (v1.05) created the database with Room and the default table name
 * `AppInfoEntity` (Kotlin class name). The rewrite uses the canonical lowercase
 * `appinfoentity`, so an in-place upgrade needs to move the legacy rows into the new table.
 * [MIGRATION_1_2] also rebuilds databases created by early 2.0.0 builds that declared
 * version 1 with the new table name (otherwise Room's identity-hash check would fail).
 *
 * SQLite table names are case-insensitive: renaming the old table to a temporary name first
 * is essential, otherwise `CREATE TABLE appinfoentity` would address the legacy table and the
 * copy would silently read from the newly created (empty) table.
 *
 * The database name [NAME] must not change: the legacy app created the very same file.
 */
@Database(entities = [AppInfoEntity::class], version = 2, exportSchema = false)
abstract class AppInfoDb : RoomDatabase() {

    abstract fun appInfoDao(): AppInfoDao

    companion object {
        const val NAME = "app-info-db"

        private const val CANONICAL_TABLE = "appinfoentity"
        private const val LEGACY_TABLE = "AppInfoEntity"
        private const val MIGRATION_TMP_TABLE = "appinfoentity_migration_tmp"

        /** Exactly what Room generates for [AppInfoEntity]. */
        private const val CREATE_TABLE = "CREATE TABLE IF NOT EXISTS `appinfoentity` " +
            "(`pkg` TEXT NOT NULL, `name` TEXT NOT NULL, `last_selected` INTEGER, " +
            "PRIMARY KEY(`pkg`))"

        private const val COPY_COLUMNS = "`pkg`, `name`, `last_selected`"

        /** v1 (legacy / early rewrite table name) -> v2 (canonical schema), preserving rows. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val sourceTable = when {
                    tableExists(db, LEGACY_TABLE) -> LEGACY_TABLE
                    tableExists(db, CANONICAL_TABLE) -> CANONICAL_TABLE
                    else -> null
                }
                if (sourceTable == null) {
                    db.execSQL(CREATE_TABLE)
                    return
                }

                // Rename first: the new table name would otherwise resolve (case-insensitively)
                // to the source table and the copy below would insert zero rows.
                db.execSQL("ALTER TABLE `$sourceTable` RENAME TO `$MIGRATION_TMP_TABLE`")
                db.execSQL(CREATE_TABLE)
                db.execSQL(
                    "INSERT OR REPLACE INTO `$CANONICAL_TABLE` ($COPY_COLUMNS) " +
                        "SELECT $COPY_COLUMNS FROM `$MIGRATION_TMP_TABLE`",
                )
                db.execSQL("DROP TABLE `$MIGRATION_TMP_TABLE`")
            }
        }

        private fun tableExists(db: SupportSQLiteDatabase, name: String): Boolean =
            db.query(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf<Any>(name),
            ).use { it.moveToFirst() }

        /**
         * Single place where the database is built so DI only has to provide the
         * instance: `@Provides fun provideAppInfoDb(@ApplicationContext context: Context) = AppInfoDb.create(context)`.
         */
        fun create(context: Context): AppInfoDb =
            Room.databaseBuilder(context.applicationContext, AppInfoDb::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

package vegabobo.languageselector.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app history database. Version 1 matches the pre-rewrite schema so that an
 * in-place upgrade keeps the existing history rows readable.
 *
 * The name [NAME] must not change: the legacy app created the very same file.
 */
@Database(entities = [AppInfoEntity::class], version = 1, exportSchema = false)
abstract class AppInfoDb : RoomDatabase() {

    abstract fun appInfoDao(): AppInfoDao

    companion object {
        const val NAME = "app-info-db"

        /**
         * Single place where the database is built so DI only has to provide the
         * instance: `@Provides fun provideAppInfoDb(@ApplicationContext context: Context) = AppInfoDb.create(context)`.
         */
        fun create(context: Context): AppInfoDb =
            Room.databaseBuilder(context.applicationContext, AppInfoDb::class.java, NAME).build()
    }
}

package vegabobo.languageselector.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import vegabobo.languageselector.core.contract.AppCatalog
import vegabobo.languageselector.core.contract.HistoryStore
import vegabobo.languageselector.core.contract.LocaleCatalog
import vegabobo.languageselector.core.contract.LocaleRemote
import vegabobo.languageselector.core.contract.LocaleStates
import vegabobo.languageselector.core.contract.PinnedLocales
import vegabobo.languageselector.core.contract.ServiceController
import vegabobo.languageselector.core.contract.SettingsStore
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.data.DefaultAppCatalog
import vegabobo.languageselector.data.DefaultHistoryStore
import vegabobo.languageselector.data.DefaultLocaleCatalog
import vegabobo.languageselector.data.DefaultLocaleStates
import vegabobo.languageselector.data.DefaultPinnedLocales
import vegabobo.languageselector.data.db.AppInfoDb
import vegabobo.languageselector.data.prefs.SharedPrefsSettingsStore
import vegabobo.languageselector.service.ServiceManager
import vegabobo.languageselector.service.UserServiceRemote
import javax.inject.Singleton

/**
 * Application level bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    // --- Privileged service (Shizuku / Root) ---

    @Binds
    @Singleton
    abstract fun bindServiceController(impl: ServiceManager): ServiceController

    @Binds
    @Singleton
    abstract fun bindLocaleRemote(impl: UserServiceRemote): LocaleRemote

    // --- Data layer ---

    @Binds
    @Singleton
    abstract fun bindAppCatalog(impl: DefaultAppCatalog): AppCatalog

    @Binds
    @Singleton
    abstract fun bindLocaleStates(impl: DefaultLocaleStates): LocaleStates

    @Binds
    @Singleton
    abstract fun bindLocaleCatalog(impl: DefaultLocaleCatalog): LocaleCatalog

    @Binds
    @Singleton
    abstract fun bindPinnedLocales(impl: DefaultPinnedLocales): PinnedLocales

    @Binds
    @Singleton
    abstract fun bindHistoryStore(impl: DefaultHistoryStore): HistoryStore

    @Binds
    @Singleton
    abstract fun bindSettingsStore(impl: SharedPrefsSettingsStore): SettingsStore

    companion object {

        @Provides
        @Singleton
        fun provideAppDispatchers(): AppDispatchers = AppDispatchers()

        @Provides
        @Singleton
        fun provideAppInfoDb(@ApplicationContext context: Context): AppInfoDb =
            AppInfoDb.create(context)
    }
}

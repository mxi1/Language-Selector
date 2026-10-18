package vegabobo.languageselector.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
import vegabobo.languageselector.data.prefs.SharedPrefsSettingsStore
import vegabobo.languageselector.di.stubs.StubAppCatalog
import vegabobo.languageselector.di.stubs.StubHistoryStore
import vegabobo.languageselector.di.stubs.StubLocaleCatalog
import vegabobo.languageselector.di.stubs.StubLocaleRemote
import vegabobo.languageselector.di.stubs.StubLocaleStates
import vegabobo.languageselector.di.stubs.StubPinnedLocales
import vegabobo.languageselector.di.stubs.StubServiceController
import javax.inject.Singleton

/**
 * Application level bindings.
 *
 * NOTE: while the rewrite is in progress the service/data contracts are bound to
 * stubs; they are swapped for the real implementations during integration.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindServiceController(impl: StubServiceController): ServiceController

    @Binds
    @Singleton
    abstract fun bindLocaleRemote(impl: StubLocaleRemote): LocaleRemote

    @Binds
    @Singleton
    abstract fun bindAppCatalog(impl: StubAppCatalog): AppCatalog

    @Binds
    @Singleton
    abstract fun bindLocaleStates(impl: StubLocaleStates): LocaleStates

    @Binds
    @Singleton
    abstract fun bindLocaleCatalog(impl: StubLocaleCatalog): LocaleCatalog

    @Binds
    @Singleton
    abstract fun bindPinnedLocales(impl: StubPinnedLocales): PinnedLocales

    @Binds
    @Singleton
    abstract fun bindHistoryStore(impl: StubHistoryStore): HistoryStore

    @Binds
    @Singleton
    abstract fun bindSettingsStore(impl: SharedPrefsSettingsStore): SettingsStore

    companion object {
        @Provides
        @Singleton
        fun provideAppDispatchers(): AppDispatchers = AppDispatchers()
    }
}

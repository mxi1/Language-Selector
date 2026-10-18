package vegabobo.languageselector

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import vegabobo.languageselector.ui.components.AppIconFetcher

@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val appContext = this
        // Lazy app icon loading through Coil, with a memory cache sized for a large app list.
        Coil.setImageLoader(
            ImageLoaderFactory {
                ImageLoader.Builder(appContext)
                    .components { add(AppIconFetcher.Factory(appContext)) }
                    .memoryCache {
                        MemoryCache.Builder(appContext)
                            .maxSizePercent(0.12)
                            .build()
                    }
                    .build()
            }
        )
    }
}

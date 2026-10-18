package vegabobo.languageselector.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vegabobo.languageselector.R

/** Coil data key for an app icon. [version] keeps the cache key in sync with app updates. */
data class AppIconRequest(val packageName: String, val version: Long)

/**
 * Loads app icons lazily from the PackageManager and lets Coil manage the memory cache,
 * instead of eagerly decoding every icon while building the app list.
 */
class AppIconFetcher(
    private val context: Context,
    private val data: AppIconRequest,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(
                data.packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
            val drawable = info.loadIcon(context.packageManager)
            DrawableResult(
                drawable = drawable,
                isSampled = false,
                dataSource = DataSource.MEMORY,
            )
        }.getOrNull()
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconRequest> {
        override fun create(
            data: AppIconRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = AppIconFetcher(context, data)
    }
}

@Composable
fun AppIcon(
    packageName: String,
    version: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = AppIconRequest(packageName, version),
        contentDescription = contentDescription,
        modifier = modifier.size(44.dp),
        contentScale = ContentScale.Fit,
        error = painterResource(R.drawable.icon_placeholder),
        placeholder = painterResource(R.drawable.icon_placeholder),
    )
}

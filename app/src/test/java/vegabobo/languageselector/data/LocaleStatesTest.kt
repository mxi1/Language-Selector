package vegabobo.languageselector.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vegabobo.languageselector.core.contract.AppCatalog
import vegabobo.languageselector.core.contract.LocaleRemote
import vegabobo.languageselector.core.dispatchers.AppDispatchers
import vegabobo.languageselector.core.model.AppLocaleState
import vegabobo.languageselector.core.model.AppSummary

/** Tests for the per-app locale state cache using fake contracts (no Android). */
class LocaleStatesTest {

    @Test
    fun `refresh queries packages in batches of 50 and caches every app`() = runTest {
        val packages = (1..120).map { "pkg.$it" }
        val remote = StatesFakeLocaleRemote(tagsProvider = { listOf("fr-FR") })
        val states = DefaultLocaleStates(remote, StatesFakeAppCatalog(packages), testDispatchers())

        states.refresh()

        assertEquals(listOf(50, 50, 20), remote.requestedBatches.map { it.size })
        assertEquals(packages.toSet(), states.states.value.keys)
        assertEquals(listOf("fr-FR"), states.stateOf("pkg.1").languageTags)
    }

    @Test
    fun `refresh with no installed apps clears the cache`() = runTest {
        val remote = StatesFakeLocaleRemote()
        val states = DefaultLocaleStates(remote, StatesFakeAppCatalog(emptyList()), testDispatchers())

        states.refresh()

        assertTrue(states.states.value.isEmpty())
        assertTrue(remote.requestedBatches.isEmpty())
        assertEquals(AppLocaleState(), states.stateOf("pkg.1"))
    }

    @Test
    fun `refresh loads the catalog first when it is still empty`() = runTest {
        val remote = StatesFakeLocaleRemote(tagsProvider = { listOf("ja-JP") })
        val catalog = LazyStatesFakeAppCatalog(listOf("pkg.a"))
        val states = DefaultLocaleStates(remote, catalog, testDispatchers())

        states.refresh()

        assertEquals(1, catalog.refreshCount)
        assertEquals(listOf("ja-JP"), states.stateOf("pkg.a").languageTags)
    }

    @Test
    fun `set and reset update the cache in place without re-querying`() = runTest {
        val remote = StatesFakeLocaleRemote()
        val states = DefaultLocaleStates(
            remote,
            StatesFakeAppCatalog(listOf("pkg.a", "pkg.b")),
            testDispatchers(),
        )

        states.setLocale("pkg.a", listOf("de-DE"))
        assertEquals(AppLocaleState(listOf("de-DE")), states.stateOf("pkg.a"))
        assertEquals("pkg.a" to listOf("de-DE"), remote.setCalls.last())
        assertTrue(remote.requestedBatches.isEmpty())

        states.resetLocale("pkg.a")
        assertEquals(AppLocaleState(), states.stateOf("pkg.a"))
        assertEquals("pkg.a" to emptyList<String>(), remote.setCalls.last())
        assertTrue(remote.requestedBatches.isEmpty())
    }

    @Test
    fun `set locale failure is propagated and keeps the cache unchanged`() = runTest {
        val remote = StatesFakeLocaleRemote(failSet = true)
        val states = DefaultLocaleStates(
            remote,
            StatesFakeAppCatalog(listOf("pkg.a")),
            testDispatchers(),
        )

        var failure: Throwable? = null
        try {
            states.setLocale("pkg.a", listOf("de-DE"))
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(AppLocaleState(), states.stateOf("pkg.a"))
    }

    @Test
    fun `force stop is forwarded to the remote`() = runTest {
        val remote = StatesFakeLocaleRemote()
        val states = DefaultLocaleStates(remote, StatesFakeAppCatalog(emptyList()), testDispatchers())

        states.forceStop("pkg.a")

        assertEquals(listOf("pkg.a"), remote.forceStopped)
    }

    private fun TestScope.testDispatchers(): AppDispatchers {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
    }
}

private class StatesFakeAppCatalog(packages: List<String>) : AppCatalog {
    override val apps: StateFlow<List<AppSummary>> = MutableStateFlow(
        packages.map { packageName -> AppSummary(packageName = packageName, label = packageName, isSystem = false) }
    )

    override suspend fun refresh() = Unit

    override fun find(packageName: String): AppSummary? =
        apps.value.firstOrNull { it.packageName == packageName }
}

private class LazyStatesFakeAppCatalog(private val packages: List<String>) : AppCatalog {
    private val mutableApps = MutableStateFlow<List<AppSummary>>(emptyList())
    override val apps: StateFlow<List<AppSummary>> = mutableApps

    var refreshCount = 0
        private set

    override suspend fun refresh() {
        refreshCount += 1
        mutableApps.value = packages.map { packageName ->
            AppSummary(packageName = packageName, label = packageName, isSystem = false)
        }
    }

    override fun find(packageName: String): AppSummary? =
        apps.value.firstOrNull { it.packageName == packageName }
}

private class StatesFakeLocaleRemote(
    private val systemLocales: List<String> = emptyList(),
    private val tagsProvider: (String) -> List<String> = { emptyList() },
    private val failSet: Boolean = false,
) : LocaleRemote {

    val requestedBatches = mutableListOf<List<String>>()
    val setCalls = mutableListOf<Pair<String, List<String>>>()
    val forceStopped = mutableListOf<String>()

    override suspend fun getUid(): Int = -1

    override suspend fun setApplicationLocales(packageName: String, languageTags: List<String>) {
        if (failSet) throw IllegalStateException("remote failure")
        setCalls += packageName to languageTags
    }

    override suspend fun getApplicationLocalesTags(packageNames: List<String>): Map<String, List<String>> {
        requestedBatches += packageNames
        return packageNames.associateWith(tagsProvider)
    }

    override suspend fun getSystemLocales(): List<String> = systemLocales

    override suspend fun forceStopPackage(packageName: String) {
        forceStopped += packageName
    }

    override suspend fun firstRunningTaskPackage(): String = ""
}

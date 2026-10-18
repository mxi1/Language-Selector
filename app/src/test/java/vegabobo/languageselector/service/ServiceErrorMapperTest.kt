package vegabobo.languageselector.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vegabobo.languageselector.core.model.ServiceFailure
import java.util.concurrent.TimeoutException

class ServiceErrorMapperTest {

    @Test
    fun `not ready exception keeps its own failure`() {
        val error = ServiceNotReadyException(ServiceFailure.PERMISSION_DENIED, "no permission")
        assertEquals(ServiceFailure.PERMISSION_DENIED, mapServiceFailure(error))
    }

    @Test
    fun `service exception keeps its own failure`() {
        val error = ServiceException(ServiceFailure.ROOT_DENIED, "root denied")
        assertEquals(ServiceFailure.ROOT_DENIED, mapServiceFailure(error))
    }

    @Test
    fun `coroutine timeout maps to timeout`() {
        val error = captureTimeout() ?: error("Expected a timeout")
        assertEquals(ServiceFailure.TIMEOUT, mapServiceFailure(error))
    }

    @Test
    fun `java timeout maps to timeout`() {
        assertEquals(ServiceFailure.TIMEOUT, mapServiceFailure(TimeoutException("slow")))
    }

    @Test
    fun `security exception maps to permission denied`() {
        assertEquals(ServiceFailure.PERMISSION_DENIED, mapServiceFailure(SecurityException("denied")))
    }

    @Test
    fun `illegal state maps to service unavailable`() {
        assertEquals(
            ServiceFailure.SERVICE_UNAVAILABLE,
            mapServiceFailure(IllegalStateException("binder haven't been received")),
        )
    }

    @Test
    fun `missing hidden api method maps to remote error`() {
        assertEquals(ServiceFailure.REMOTE_ERROR, mapServiceFailure(NoSuchMethodError("getTasks")))
    }

    @Test
    fun `unknown throwable maps to unknown`() {
        assertEquals(ServiceFailure.UNKNOWN, mapServiceFailure(RuntimeException("boom")))
    }

    @Test
    fun `cause chain is inspected`() {
        val error = RuntimeException("wrapper", IllegalStateException("binder is gone"))
        assertEquals(ServiceFailure.SERVICE_UNAVAILABLE, mapServiceFailure(error))
    }

    @Test
    fun `binder type names are classified`() {
        assertEquals(ServiceFailure.BINDER_DIED, classifyFailureType("android.os.DeadObjectException"))
        assertEquals(ServiceFailure.REMOTE_ERROR, classifyFailureType("android.os.RemoteException"))
        assertEquals(ServiceFailure.REMOTE_ERROR, classifyFailureType("android.os.ServiceSpecificException"))
        assertEquals(ServiceFailure.ROOT_DENIED, classifyFailureType("com.topjohnwu.superuser.NoShellException"))
        assertEquals(ServiceFailure.TIMEOUT, classifyFailureType("java.util.concurrent.TimeoutException"))
        assertNull(classifyFailureType("java.lang.RuntimeException"))
        assertNull(classifyFailureType(null))
    }

    private fun captureTimeout(): Throwable? = runBlocking {
        try {
            withTimeout(1L) { delay(1_000L) }
            null
        } catch (throwable: Throwable) {
            throwable
        }
    }
}

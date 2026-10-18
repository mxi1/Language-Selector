package vegabobo.languageselector.service

import kotlinx.coroutines.TimeoutCancellationException
import vegabobo.languageselector.core.model.ServiceFailure
import java.util.concurrent.TimeoutException

/**
 * Failure raised by the service layer. Callers only need to inspect [failure] to decide
 * how to present the error to the user; the original exception stays available as [cause].
 */
open class ServiceException(
    val failure: ServiceFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The privileged user service is not connected (yet). */
class ServiceNotReadyException(
    failure: ServiceFailure,
    message: String,
) : ServiceException(failure, message)

private const val MAX_CAUSE_DEPTH = 8

/**
 * Maps any [Throwable] thrown while talking to the privileged service to a [ServiceFailure].
 *
 * The mapper is a pure function: it never touches Android classes, only class names. That
 * keeps it unit testable without an Android runtime, while Android binder exceptions
 * (`DeadObjectException`, `RemoteException`, ...) are still recognised through the type
 * hierarchy of the thrown object.
 */
internal fun mapServiceFailure(throwable: Throwable): ServiceFailure {
    var current: Throwable? = throwable
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        val mapped = mapSingleFailure(current)
        if (mapped != null) return mapped
        val cause = current.cause
        current = if (cause === current) null else cause
        depth++
    }
    return ServiceFailure.UNKNOWN
}

private fun mapSingleFailure(throwable: Throwable): ServiceFailure? {
    when (throwable) {
        is ServiceException -> return throwable.failure
        is TimeoutCancellationException -> return ServiceFailure.TIMEOUT
        is TimeoutException -> return ServiceFailure.TIMEOUT
        is SecurityException -> return ServiceFailure.PERMISSION_DENIED
        is NoSuchMethodError -> return ServiceFailure.REMOTE_ERROR
        is NoSuchFieldError -> return ServiceFailure.REMOTE_ERROR
        is LinkageError -> return ServiceFailure.REMOTE_ERROR
        is IllegalStateException -> return ServiceFailure.SERVICE_UNAVAILABLE
    }

    var type: Class<*>? = throwable.javaClass
    while (type != null) {
        val mapped = classifyFailureType(type.name)
        if (mapped != null) return mapped
        type = type.superclass
    }
    return null
}

/**
 * Classifies a throwable type name (own type or one of its super types).
 * Kept internal and Android free so the mapping table is directly unit tested.
 */
internal fun classifyFailureType(typeName: String?): ServiceFailure? = when (typeName) {
    "android.os.DeadObjectException" -> ServiceFailure.BINDER_DIED
    "android.os.RemoteException",
    "android.os.TransactionTooLargeException",
    "android.os.ServiceSpecificException",
    "java.lang.NoSuchMethodError",
    "java.lang.NoSuchFieldError",
    "java.lang.LinkageError" -> ServiceFailure.REMOTE_ERROR

    "java.util.concurrent.TimeoutException",
    "kotlinx.coroutines.TimeoutCancellationException" -> ServiceFailure.TIMEOUT

    "java.lang.SecurityException" -> ServiceFailure.PERMISSION_DENIED
    "java.lang.IllegalStateException" -> ServiceFailure.SERVICE_UNAVAILABLE
    "com.topjohnwu.superuser.NoShellException" -> ServiceFailure.ROOT_DENIED
    else -> null
}

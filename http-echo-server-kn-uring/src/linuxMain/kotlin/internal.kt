import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.pointed
import liburing.io_uring
import liburing.io_uring_cqe
import kotlin.coroutines.*

internal class URingContext(
    val ring: CPointer<io_uring>
) : AbstractCoroutineContextElement(URingContext) {

    companion object : CoroutineContext.Key<URingContext>
}

internal class StartContinuation(
    override val context: CoroutineContext = EmptyCoroutineContext
) : Continuation<Unit> {
    override fun resumeWith(result: Result<Unit>) {
        result.onFailure {
            println("StartContinuation failed")
            it.printStackTrace()
        }
    }
}

internal suspend inline fun getRing(): URingContext {
    return coroutineContext[URingContext] ?: throw Exception("no URingContext found")
}


internal fun handleAccept(ring: CPointer<io_uring>, cqe: CPointer<io_uring_cqe>, userData: StableRef<IUserData<*>>) {
    val acceptData = userData.get() as AcceptUserData
    val clientFd = cqe.pointed.res
    acceptData.continuation.resume(ClientSocket(clientFd))
    userData.dispose()
}

internal fun handleRead(ring: CPointer<io_uring>, cqe: CPointer<io_uring_cqe>, userData: StableRef<IUserData<*>>) {
    val readData = userData.get() as ReadUserData
    val res = cqe.pointed.res
    readData.continuation.resume(res)
    userData.dispose()
}

internal fun handleWrite(ring: CPointer<io_uring>, cqe: CPointer<io_uring_cqe>, userData: StableRef<IUserData<*>>) {
    val writeData = userData.get() as WriteUserData
    val res = cqe.pointed.res
    writeData.continuation.resume(res)
    userData.dispose()
}

internal fun handleTimeout(ring: CPointer<io_uring>, cqe: CPointer<io_uring_cqe>, userData: StableRef<IUserData<*>>) {

}

internal val HTTP_OK = "HTTP/1.1 200 OK\r\n"
internal val HEAD_SERVER = "Server: laidianniu\r\n"
internal val HEAD_NEW_LINE = "\r\n"
internal val HEAD_CONNECTION = "Connection: close\r\n"
internal val HEAD_CONTENT_TYPE = "Content-Type: application/json\r\n"

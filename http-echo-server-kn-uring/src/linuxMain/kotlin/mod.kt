import kotlinx.cinterop.*
import liburing.*
import platform.posix.perror
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine


public fun start(block: suspend () -> Unit) {
    memScoped {
        val ring = alloc<io_uring>()
        io_uring_queue_init(4096u, ring.ptr, 0u)
        val coroutine = block.createCoroutine(StartContinuation(URingContext(ring.ptr)))
        coroutine.resume(Unit)

        val cqe: CPointerVar<io_uring_cqe> = alloc()

        // executor
        while (true) {

            val ret = io_uring_wait_cqe(ring.ptr, cqe.ptr)
            if (ret < 0) {
                perror("io_uring_wait_cqe")
                throw Exception("io_uring_wait_cqe error")
            }
            if (cqe.value!!.pointed.res < 0) {
                throw Exception("io_uring_wait_cqe_error")
            }
            val dataRef = io_uring_cqe_get_data(cqe.value)!!.asStableRef<IUserData<*>>()
            val data = dataRef.get()
            // println("type: ${data.eventType}")
            when(data.eventType) {
                EventType.Accept -> handleAccept(ring.ptr, cqe.value!!, dataRef)
                EventType.Read -> handleRead(ring.ptr, cqe.value!!, dataRef)
                EventType.Write -> handleWrite(ring.ptr, cqe.value!!, dataRef)
                else -> {
                    throw Exception("EventType ${data.eventType} not supported")
                }
            }
            io_uring_cqe_seen(ring.ptr, cqe.value)
        }
    }
}

public suspend fun launch(block: suspend () -> Unit) {
    block.startCoroutine(StartContinuation(getRing()))
}

public class HttpRequest(
    public val method: String,
    public val path: String,
    public val version: String,
    public val headers: List<Pair<String, String>>,
    public val body: ByteArray
) {

    override fun toString(): String {
        return buildString {
            append("HTTP Request").append("\n")
            append("method: $method\n")
            append("path: $path\n")
            append("version: $version\n")
            append("headers: $headers\n")
            append("body: ${body.toKString()}\n")
        }
    }
}


public fun parseRequest(data: ByteArray): HttpRequest {
    var position = readLineIndex(data, 0)
    if (position == data.size) {
        throw HttpParseException("request line not found")
    }
    // position += 2
    val requestLine = data.sliceArray(0 until (position - 1)).toKString()
    val requestLineSplit = requestLine.split(' ')
    val (method, path, version) = requestLineSplit
    val headers = mutableListOf<Pair<String, String>>()

    while (true) {
        val oldPos = position + 1
        position = readLineIndex(data, position)
        val line = data.sliceArray(oldPos until (position - 1)).toKString()
        if (line.trim().isEmpty()) break
        val split = line.split(": ")
        if (split.size < 2) {
            println("error in parse header, $line")
        } else {
            headers += split.first() to split.last()
        }

    }
    val remaining = data.size - position
    var contentLen = 0
    for ((key, value) in headers) {
        if (key.equals("Content-Length", ignoreCase = true)) {
            // println("contentlen: $key $value")
            contentLen = value.toInt()
        }
    }

    val body: ByteArray
    if (remaining < contentLen) {
        throw Exception("remaining < contentLen")
    }
    if (contentLen > 0) {
        body = ByteArray(contentLen)
        data.copyInto(body, 0, position, position + contentLen)
    } else {
        body = byteArrayOf()
    }

    return HttpRequest(method, path, version, headers, body)
}

@OptIn(ExperimentalStdlibApi::class)
public fun HttpRequest.buildEchoResponse(): ByteArray {
    val body = buildString {
        append("{")
        append("\"method\": \"${method}\"").append(",")
        append("\"path\": \"${path}\"").append(",")
        append("\"version\": \"${version}\"").append(",")
        append("\"headers\": {")
        for (header in headers) {
            append("\"${header.first}\": \"${header.second}\"").append(',')
        }
        set(length - 1, '}')
        append(',')
        append("\"body\": \"")
        append(body.toHexString())
        append("\"")
        append("}")
    }

    return buildString {
        append(HTTP_OK)
        append(HEAD_SERVER)
        append(HEAD_CONNECTION)
        append(HEAD_CONTENT_TYPE)
        append("Content-Length: ${body.length}\r\n")
        append(HEAD_NEW_LINE)
        append(body)
    }.encodeToByteArray()
}
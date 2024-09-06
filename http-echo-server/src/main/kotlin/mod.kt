package laidianniu

import java.nio.ByteBuffer
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.Base64
import kotlin.coroutines.*

// region coroutine builders

public fun start(block: suspend () -> Unit) {
    val selectorContext = SelectorElement()
    val selector = selectorContext.selector
    val coroutine = block.createCoroutine(StartContinuation(selectorContext))
    coroutine.resume(Unit)

    // executor
    while (true) {
        try {
            selector.select()
            val keyIterator = selector.selectedKeys().iterator()
            while (keyIterator.hasNext()) {
                val key = keyIterator.next()
                keyIterator.remove()

                when {
                    key.isAcceptable -> handleAccept(key)
                    key.isReadable -> handleRead(key)
                    key.isWritable -> handleWrite(key)
                    else -> {
                        throw UnsupportedOperationException("OP ${key.readyOps()} not supported")
                    }
                }
                // key.cancel()
            }
        } catch (e: ClosedSelectorException) {
            println("coroutine canceled")
            break
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

public suspend fun launch(block: suspend () -> Unit) {
    block.startCoroutine(StartContinuation(getSelector()))
}

public suspend fun terminate() {
    @Suppress("BlockingMethodInNonBlockingContext")
    getSelector().selector.close()
}

// endregion


// region IO operations

public suspend fun ServerSocketChannel.acceptAsync(): SocketChannel {
    val selectorContext = getSelector()
    return suspendCoroutine { continuation ->
        val key = register(selectorContext.selector, SelectionKey.OP_ACCEPT)
        val attachment = AcceptAttachment(this, continuation)
        key.attach(attachment)
    }
}

public suspend fun SocketChannel.readAsync(buffer: ByteBuffer): Int {
    val selectorContext = getSelector()
    return suspendCoroutine { continuation ->
        val key = register(selectorContext.selector, SelectionKey.OP_READ)
        val attach = ReadAttachment(this, buffer, continuation)
        key.attach(attach)
    }
}

public suspend fun SocketChannel.writeAsync(buffer: ByteBuffer) {
    val selectorContext = getSelector()
    return suspendCoroutine { continuation ->
        val key = register(selectorContext.selector, SelectionKey.OP_WRITE)
        val attach = WriteAttachment(this, buffer, continuation)
        key.attach(attach)
    }
}

// endregion

public class HttpRequest(
    public val method: String,
    public val path: String,
    public val version: String,
    public val headers: List<Pair<String, String>>,
    public val body: ByteArray
)

public fun parseRequest(buffer: ByteBuffer): HttpRequest {
    val requestLine = buffer.readLine()

    val split = requestLine.split(' ')
    val (method, path, version) = split
    val headers = ArrayList<Pair<String, String>>()
    while (true) {
        val line = buffer.readLine()
        if (line.isEmpty()) break
        val (key, value) = line.split(' ')
        headers.add(key to value.trim())
    }
    val remaining = buffer.remaining()
    val body = if (remaining > 0) {
        ByteArray(remaining).apply(buffer::get)
    } else byteArrayOf()

    return HttpRequest(method, path, version, headers, body)
}

public fun HttpRequest.buildEchoResponse(): ByteBuffer {
    val buffer = ByteBuffer.allocate(8192)
    buffer.put(HTTP_OK)
    buffer.put(HEAD_SERVER)
    val body = buildString {
        // json
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
        append(Base64.getEncoder().encodeToString(body))
        append("\"")
        append("}")
    }.toByteArray()
    val contentLen = "Content-Length: ${body.size}\r\n".toByteArray()
    buffer.put(contentLen)
    buffer.put(HEAD_CONNECTION)
    buffer.put(HEAD_CONTENT_TYPE)
    buffer.put(HEAD_NEW_LINE)
    buffer.put(body)
    buffer.flip()
    return buffer
}

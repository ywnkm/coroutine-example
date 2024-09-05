package laidianniu

import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import kotlin.coroutines.*

internal class StartContinuation(
    override val context: CoroutineContext = EmptyCoroutineContext
) : Continuation<Unit> {

    override fun resumeWith(result: Result<Unit>) {
        result.onFailure {
            println("Program finished with failure")
            it.printStackTrace()
        }
    }
}

internal class SelectorElement(
    val selector: Selector
) : AbstractCoroutineContextElement(SelectorElement) {

    constructor(): this(Selector.open())

    companion object Key : CoroutineContext.Key<SelectorElement>
}


internal suspend inline fun getSelector(): SelectorElement {
    return coroutineContext[SelectorElement] ?: throw IllegalStateException("no selector found")
}


internal fun handleAccept(key: SelectionKey) {
    val (serverChannel, con) = key.attachment() as? AcceptAttachment
        ?: throw IllegalStateException("no attachment found")
    val client = serverChannel.accept()
        ?: throw IllegalStateException("no accepted selection key found")
    client.configureBlocking(false)
    con.resume(client)
}

internal fun handleRead(key: SelectionKey) {
    val (socketChannel, buffer, con) = key.attachment() as? ReadAttachment
        ?: throw IllegalStateException("no attachment found")

    val result = socketChannel.read(buffer)
    if (result == 0) {
        // throw IllegalStateException("")
        System.err.println("no read available")
    }
    con.resume(result)
}

internal fun handleWrite(key: SelectionKey, selector: Selector) {
    val attach = key.attachment() as? WriteAttachment
        ?: throw IllegalStateException("no attachment found")
    val res = attach.socketChannel.write(attach.buffer)
    if (attach.buffer.remaining() > 0) {
        val newKey = attach.socketChannel.register(selector, SelectionKey.OP_WRITE)
        newKey.attach(attach)
    } else {
        attach.continuation.resume(Unit)
    }
}

internal fun ByteBuffer.readLine(): String {
    val line = StringBuilder()
    while (hasRemaining()) {
        val ch = get().toInt().toChar()
        if (ch == '\r') {
            if (hasRemaining() && get().toInt().toChar() == '\n') {
                break
            }
        }
        line.append(ch)
    }
    return line.toString()
}

internal val HTTP_OK = "HTTP/1.1 200 OK\r\n".toByteArray()
internal val HEAD_SERVER = "Server: laidianniu\r\n".toByteArray()
internal val HEAD_NEW_LINE = "\r\n".toByteArray()
internal val HEAD_CONNECTION = "Connection: close\r\n".toByteArray()
internal val HEAD_CONTENT_TYPE = "Content-Type: application/json\r\n".toByteArray()

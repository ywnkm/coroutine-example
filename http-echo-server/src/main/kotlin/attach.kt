package laidianniu

import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import kotlin.coroutines.Continuation

public sealed interface IAttachment<in T> {

    public val continuation: Continuation<T>

}

public data class AcceptAttachment(
    public val serverSocketChannel: ServerSocketChannel,
    override val continuation: Continuation<SocketChannel>
) : IAttachment<SocketChannel>


public data class ReadAttachment(
    public val socketChannel: SocketChannel,
    public val buffer: ByteBuffer,
    override val continuation: Continuation<Int>
) : IAttachment<Int>

public data class WriteAttachment(
    public val socketChannel: SocketChannel,
    public val buffer: ByteBuffer,
    override val continuation: Continuation<Unit>
) : IAttachment<Unit>

import kotlin.coroutines.Continuation


internal enum class EventType {
    Accept,
    Read,
    Write,
    Timeout
}

internal interface IUserData<T> {
    val eventType: EventType
    val continuation: Continuation<T>
}

internal class AcceptUserData(
    override val continuation: Continuation<ClientSocket>,
) : IUserData<ClientSocket> {

    override val eventType: EventType
        get() = EventType.Accept
}

internal class ReadUserData(
    override val continuation: Continuation<Int>,
    val buf: ByteArray
) : IUserData<Int> {

    override val eventType: EventType
        get() = EventType.Read
}

internal class WriteUserData(
    override val continuation: Continuation<Int>,
    val buf: ByteArray
) : IUserData<Int> {

    override val eventType: EventType
        get() = EventType.Write
}
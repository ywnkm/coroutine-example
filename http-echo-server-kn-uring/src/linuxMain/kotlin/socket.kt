import kotlinx.cinterop.*
import liburing.*
import platform.posix.*
import platform.posix.PF_INET
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.listen
import platform.posix.socket
import platform.posix.setsockopt
import kotlin.coroutines.suspendCoroutine

public class ServerSocket(public val port: Int) : AutoCloseable {
    public val fd: Int

    init {
        fd = socket(PF_INET, SOCK_STREAM, 0)
        if (fd < 0) {
            perror("socket")
            throw Exception("socket error")
        }
    }

    private val memScope = MemScope()

    public fun bind() {
        memScoped {
            val addrIn = alloc<sockaddr_in>()
            val enable = alloc<IntVar>()
            if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, enable.ptr, 4u) < 0) {
                perror("setsockopt(SO_REUSEADDR)")
                throw Exception("socket error")
            }
            memset(addrIn.ptr, 0, sizeOf<sockaddr_in>().convert())
            addrIn.sin_family = platform.posix.AF_INET.convert()
            addrIn.sin_port = htons(port.convert())
            addrIn.sin_addr.s_addr = htons(INADDR_ANY.convert()).convert()
            if (bind(fd, addrIn.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()) < 0) {
                perror("bind")
                throw Exception("bind error")
            }
        }
    }

    public fun listen() {
        if (listen(fd, 10) < 0) {
            println("listen")
            throw Exception("listen error")
        }
    }

    public suspend fun accept(): ClientSocket {
        val ring = getRing().ring
        val sqe = io_uring_get_sqe(ring)!!
        val clientAddr = memScope.alloc<sockaddr_in>()
        // val sockLen = sizeOf<sockaddr_in>()
        val sockLen = memScope.alloc<UIntVar>()
        sockLen.value = sizeOf<sockaddr_in>().toUInt()
        return suspendCoroutine {
            io_uring_prep_accept(sqe, fd, clientAddr.ptr.reinterpret(), sockLen.ptr,0)
            val data = AcceptUserData(it)
            val ref = StableRef.create(data)
            io_uring_sqe_set_data(sqe, ref.asCPointer())
            io_uring_submit(ring)
        }
    }

    public override fun close() {
        platform.posix.close(fd)
    }

}

public class ClientSocket(
    public val fd: Int
) : AutoCloseable {

    public suspend fun read(buf: ByteArray): Int {
        val ring = getRing().ring
        val sqe = io_uring_get_sqe(ring)!!

        return suspendCoroutine {
            io_uring_prep_read(sqe, fd, buf.refTo(0), buf.size.convert(), 0u)
            val data = ReadUserData(it, buf)
            val ref = StableRef.create(data)
            io_uring_sqe_set_data(sqe, ref.asCPointer())
            io_uring_submit(ring)
        }
    }

    public suspend fun write(buf: ByteArray): Int {
        val ring = getRing().ring
        val sqe = io_uring_get_sqe(ring)!!

        return suspendCoroutine {
            io_uring_prep_write(sqe, fd, buf.refTo(0), buf.size.convert(), 0u)
            val data = WriteUserData(it, buf)
            val ref = StableRef.create(data)
            io_uring_sqe_set_data(sqe, ref.asCPointer())
            io_uring_submit(ring)
        }
    }

    public override fun close() {
        platform.posix.close(fd)
    }
}


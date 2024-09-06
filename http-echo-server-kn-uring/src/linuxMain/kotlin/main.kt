import kotlinx.cinterop.*
import liburing.*

@OptIn(ExperimentalForeignApi::class)
fun main() {
    memScoped {
        val ring = this.alloc<io_uring>()
        val r = io_uring_queue_init(10u, ring.ptr, 0u)
        println("r is $r")
        // TODO
    }
}

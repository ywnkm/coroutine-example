import kotlinx.cinterop.MemScope
import kotlinx.cinterop.memScoped
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
public inline fun <R> myMemScoped(block: MemScope.()->R): R  {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return memScoped(block)
}

internal fun readLine(data: ByteArray, start: Int = 0): String {
    val line = StringBuilder()
    var position = 0
    fun hasRemaining(): Boolean {
        return position < data.size
    }
    while (hasRemaining()) {
        val ch = data[position].toInt().toChar()
        if (ch == '\r') {
            if (hasRemaining()) {
                position++
                if (data[position].toInt().toChar() == '\n') {
                    break
                }
            }
        }
        line.append(ch)
        position++
    }
    return line.toString()
}

internal fun readLineIndex(data: ByteArray, start: Int = 0): Int {
    var position = start
    fun hasRemaining(): Boolean {
        return position < data.size
    }
    while (hasRemaining()) {
        val ch = data[position].toInt().toChar()
        if (ch == '\r') {
            if (hasRemaining()) {
                position++
                if (data[position].toInt().toChar() == '\n') {
                    break
                }
            }
        }
        position++
    }
    // println("line index: $position")
    return position
}

@OptIn(ExperimentalStdlibApi::class)
public fun hexString(data: ByteArray): String {
    return buildString {
        for (ch in data) {
            append(ch.toHexString())
        }
    }
}

public class HttpParseException(
    msg: String,
) : Exception(msg)

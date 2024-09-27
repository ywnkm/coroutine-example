import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import platform.posix.SIGINT
import platform.posix.SIGTERM
import platform.posix.signal
import kotlin.system.exitProcess

private val cleans: MutableList<() -> Unit> = mutableListOf()

private suspend fun asyncMain(port: Int) {
    println("async main start")

    val serverSocket = ServerSocket(port)
    cleans.add {
        serverSocket.close()
    }
    serverSocket.bind()
    serverSocket.listen()
    println("server listening on port $port")
    while (true) {
        val clientSocket = serverSocket.accept()

        launch {
            clientSocket.use {
                val buf = ByteArray(4096)
                clientSocket.read(buf)
                val request = try {
                    parseRequest(buf)
                } catch (e: HttpParseException) {
                    println("parse error ${e.message}")
                    return@use
                }
                // println(request)
                val res = request.buildEchoResponse()
                clientSocket.write(res)
            }
        }
    }
}

private fun clean() {
    println("shutting down...")
    cleans.forEach {
        it()
    }
}

private fun handleShutdown() {
    signal(SIGINT, staticCFunction { _ ->
        clean()
    })

    signal(SIGTERM, staticCFunction { _ ->
        clean()
    })
}

public fun main(args: Array<out String>) {
    var port = 8080
    if (args.isNotEmpty()) {
        try {
            port = args[0].toInt()
        } catch (e: Throwable) {
            println("error parse port: ${args[0]}")
            exitProcess(1)
        }
    }
    handleShutdown()
    start {
        asyncMain(port)
    }
"""
${args[0]}
"""
    println("end")
}

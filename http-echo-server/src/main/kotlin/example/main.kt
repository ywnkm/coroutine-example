package laidianniu.example


import laidianniu.*
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private suspend fun asyncMain(port: Int) {
    println("async main start")

    val serverChannel = ServerSocketChannel.open()
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        println("closing server...")
        serverChannel.close()
    })
    serverChannel.bind(InetSocketAddress("127.0.0.1", port))
    println("server started on port $port")
    while (true) {
        val client = serverChannel.acceptAsync()
        launch {
            val buffer = ByteBuffer.allocate(4096)
            client.readAsync(buffer)
            buffer.flip()
            if (buffer.hasRemaining()) {
               try {
                   val request = parseRequest(buffer)
                   val res = request.buildEchoResponse()
                   client.writeAsync(res)
               } catch (e: Exception) {
                   e.printStackTrace()
               }
            }
            client.close()
        }
    }
}

public fun main(args: Array<out String>) {
    var port = 8080
    if (args.isNotEmpty()) {
        try {
            port = args[0].toInt()
        } catch (e: Exception) {
            System.err.println("parse port failed: $args[0]")
            exitProcess(1)
        }
    }
    start {
        asyncMain(port)
    }
    println("end")
}

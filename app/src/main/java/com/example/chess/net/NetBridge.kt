package com.example.chess.net

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

interface NetEvents {
    fun onFen(fen: String)
    fun onRtt(ms: Int)
    fun onPeerConnected()
    fun onPeerMove(from: Int, to: Int, promo: Int)
    fun onPeerNewGame()
    fun onClosed(reason: String)
}

/**
 * LAN chess link. The host owns the board; the guest only sends move intents,
 * so the position can never diverge (host re-broadcasts FEN after every move).
 */
class NetBridge(
    private val post: ((() -> Unit) -> Unit),
    private val events: NetEvents
) {

    @Volatile private var socket: Socket? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var running = false
    /** host side: answers "who is there" requests with the current position */
    var fenProvider: (() -> String)? = null

    /** guest side: also pull the position every heartbeat */
    var requestState = false

    private var pingSeq = 0
    private var pingSentAt = 0L



    // ---------------- host ----------------

    fun startHost(port: Int, name: String) {
        running = true
        Thread {
            var ds: DatagramSocket? = null
            try {
                ds = DatagramSocket()
                ds.broadcast = true
                val msg = "CHESS_HOST|$name|${Discovery.localIp()}|$port".toByteArray()
                while (running) {
                    try {
                        ds.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), Discovery.PORT))
                    } catch (e: Exception) {
                        // wifi hiccup: try again next second
                    }
                    Thread.sleep(1000)
                }
            } catch (e: Exception) {
                // advertising is best effort only
            } finally {
                try { ds?.close() } catch (e: Exception) {}
            }
        }.also { it.isDaemon = true; it.start() }

        Thread {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                server = ss
                while (running) {
                    val s = ss.accept()
                    if (socket != null) {
                        try { s.close() } catch (e: Exception) {}
                        continue
                    }
                    try {
                        attach(s)
                    } catch (e: Exception) {
                        try { s.close() } catch (ignored: Exception) {}
                        continue
                    }
                    post { events.onPeerConnected() }
                }
            } catch (e: Exception) {
                if (running) post { events.onClosed(e.message ?: "сервер остановлен") }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    // ---------------- guest ----------------

    fun startClient(host: String, port: Int) {
        running = true
        Thread {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                attach(s)
                post { events.onPeerConnected() }
            } catch (e: Exception) {
                post { events.onClosed("не удалось подключиться: ${e.message ?: host}") }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    // ---------------- shared ----------------

    private fun attach(s: Socket) {
        socket = s
        writer = PrintWriter(s.getOutputStream(), true)
        // confirm the link so the other side does not claim "connected" too early
        sendLine("hello")
        Thread {
            while (running) {
                Thread.sleep(2000)
                pingSeq++
                pingSentAt = System.currentTimeMillis()
                sendLine("ping|$pingSeq")
                if (requestState) sendLine("want")
            }
        }.also { it.isDaemon = true }.start()
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    val parts = line.split("|")
                    when (parts[0]) {
                        "fen" -> {
                            val fen = parts.getOrNull(1) ?: ""
                            if (fen.isNotEmpty()) post { events.onFen(fen) }
                        }
                        "move" -> {
                            val f = parts.getOrNull(1)?.toIntOrNull() ?: -1
                            val t = parts.getOrNull(2)?.toIntOrNull() ?: -1
                            val p = parts.getOrNull(3)?.toIntOrNull() ?: 0
                            if (f in 0..63 && t in 0..63) post { events.onPeerMove(f, t, p) }
                        }
                        "hello" -> post { events.onPeerConnected() }
                        "new" -> post { events.onPeerNewGame() }
                        "ping" -> sendLine("pong|${parts.getOrNull(1) ?: "0"}")
                        "want" -> {
                            val provider = fenProvider
                            if (provider != null) {
                                try { sendFen(provider()) } catch (e: Exception) {}
                            }
                        }
                        "pong" -> {
                            val rtt = (System.currentTimeMillis() - pingSentAt).toInt()
                            if (rtt in 0..30000) post { events.onRtt(rtt) }
                        }
                    }
                }
            } catch (e: Exception) {
                // peer vanished
            } finally {
                post { events.onClosed("соперник отключился") }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun sendLine(line: String) {
        val w = writer ?: return
        try { w.println(line) } catch (e: Exception) {}
    }

    fun sendFen(fen: String) = sendLine("fen|$fen")

    fun sendMove(from: Int, to: Int, promo: Int) = sendLine("move|$from|$to|$promo")

    fun sendNewGame() = sendLine("new")

    fun hasPeer(): Boolean = socket != null

    fun close() {
        running = false
        try { writer?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
        try { server?.close() } catch (e: Exception) {}
        socket = null
        writer = null
        server = null
    }
}

data class HostInfo(val name: String, val ip: String, val port: Int)

/** Finds hosts advertising themselves over UDP broadcast on the local network. */
object Discovery {

    const val PORT = 8766

    fun localIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ip
                    }
                }
            }
        } catch (e: Exception) {
            // fall through
        }
        return "0.0.0.0"
    }

    fun findHosts(timeoutMs: Long): List<HostInfo> {
        val found = LinkedHashMap<String, HostInfo>()
        var ds: DatagramSocket? = null
        try {
            ds = DatagramSocket(null)
            ds.reuseAddress = true
            ds.bind(InetSocketAddress(PORT))
            ds.soTimeout = 400
            val buf = ByteArray(512)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    ds.receive(packet)
                    val text = String(packet.data, 0, packet.length)
                    val parts = text.split("|")
                    if (parts.size >= 4 && parts[0] == "CHESS_HOST") {
                        val ip = parts[2]
                        found[ip] = HostInfo(parts[1], ip, parts[3].toIntOrNull() ?: 8765)
                    }
                } catch (e: Exception) {
                    // timeout slice; keep listening
                }
            }
        } catch (e: Exception) {
            // no UDP access
        } finally {
            try { ds?.close() } catch (e: Exception) {}
        }
        return found.values.toList()
    }
}

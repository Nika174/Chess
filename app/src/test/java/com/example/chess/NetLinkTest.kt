package com.example.chess

import com.example.chess.net.NetBridge
import com.example.chess.net.NetEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the real sockets on loopback: handshake, push and pull of state, ping. */
class NetLinkTest {

    private class Sink : NetEvents {
        @Volatile var fen: String? = null
        @Volatile var connected = false
        @Volatile var rtt = -1
        @Volatile var move: String? = null
        val fenLatch = CountDownLatch(1)
        val helloLatch = CountDownLatch(1)
        val rttLatch = CountDownLatch(1)
        val moveLatch = CountDownLatch(1)

        override fun onFen(fen: String) {
            this.fen = fen
            fenLatch.countDown()
        }

        override fun onRtt(ms: Int) {
            rtt = ms
            rttLatch.countDown()
        }

        override fun onPeerConnected() {
            connected = true
            helloLatch.countDown()
        }

        override fun onPeerMove(from: Int, to: Int, promo: Int) {
            move = "$from-$to-$promo"
            moveLatch.countDown()
        }

        override fun onPeerNewGame() {}

        override fun onClosed(reason: String) {}
    }

    private val sync: ((() -> Unit) -> Unit) = { it() }

    @Test
    fun handshakeAndPositionPush() {
        val port = 18765
        val hostSink = Sink()
        val host = NetBridge(sync, hostSink)
        host.startHost(port, "test")

        val guestSink = Sink()
        val guest = NetBridge(sync, guestSink)
        guest.startClient("127.0.0.1", port)

        assertTrue("guest must see the host hello", guestSink.helloLatch.await(6, TimeUnit.SECONDS))
        assertTrue("host must accept the guest", hostSink.helloLatch.await(6, TimeUnit.SECONDS))

        host.sendFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        assertTrue("guest must receive the position", guestSink.fenLatch.await(6, TimeUnit.SECONDS))
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            guestSink.fen
        )

        host.close()
        guest.close()
    }

    @Test
    fun movesTravelBothWays() {
        val port = 18766
        val hostSink = Sink()
        val host = NetBridge(sync, hostSink)
        host.startHost(port, "test")

        val guestSink = Sink()
        val guest = NetBridge(sync, guestSink)
        guest.startClient("127.0.0.1", port)
        assertTrue(guestSink.helloLatch.await(6, TimeUnit.SECONDS))

        guest.sendMove(52, 36, 0)
        assertTrue("host must receive the guest move", hostSink.moveLatch.await(6, TimeUnit.SECONDS))
        assertEquals("52-36-0", hostSink.move)

        host.close()
        guest.close()
    }

    @Test
    fun guestPullsStateAndHeartbeatReportsRtt() {
        val port = 18767
        val hostSink = Sink()
        val host = NetBridge(sync, hostSink)
        host.fenProvider = { "PULLED-POSITION w - - 0 1" }
        host.startHost(port, "test")

        val guestSink = Sink()
        val guest = NetBridge(sync, guestSink)
        guest.requestState = true
        guest.startClient("127.0.0.1", port)

        assertTrue(guestSink.helloLatch.await(6, TimeUnit.SECONDS))
        assertTrue("guest must pull the state", guestSink.fenLatch.await(8, TimeUnit.SECONDS))
        assertEquals("PULLED-POSITION w - - 0 1", guestSink.fen)
        assertTrue("heartbeat must measure rtt", guestSink.rttLatch.await(8, TimeUnit.SECONDS))
        assertTrue(guestSink.rtt >= 0)

        host.close()
        guest.close()
    }
}

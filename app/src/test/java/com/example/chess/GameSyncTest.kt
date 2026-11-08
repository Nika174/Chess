package com.example.chess

import com.example.chess.chess.Game
import com.example.chess.net.NetBridge
import com.example.chess.net.NetEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end: two Game instances over a real socket, wired exactly like MainActivity.
 * Fails if the host forgets to broadcast, or the guest fails to apply a position.
 */
class GameSyncTest {

    private class Peer(val game: Game, private val isHost: Boolean) : NetEvents {
        var link: NetBridge? = null
        val applied = AtomicInteger(0)
        val hello = CountDownLatch(1)
        var badFen: String? = null
        var lastFen = CountDownLatch(1)

        override fun onFen(fen: String) {
            if (!game.setFromFen(fen)) badFen = fen
            applied.incrementAndGet()
            lastFen.countDown()
        }

        override fun onRtt(ms: Int) {}
        override fun onPeerConnected() { hello.countDown() }

        override fun onPeerMove(from: Int, to: Int, promo: Int) {
            if (!isHost) return
            val legal = game.allLegalMoves().firstOrNull {
                it.from == from && it.to == to && (promo == 0 || it.promo == promo)
            }
            if (legal != null) {
                game.makeMove(legal)
                link?.sendFen(game.toFen())
            }
        }

        override fun onPeerNewGame() {}
        override fun onClosed(reason: String) {}
    }

    @Test
    fun hostMoveReachesGuestAndBoardsMatch() {
        val port = 18780
        val host = Peer(Game(), true)
        val guest = Peer(Game(), false)

        val hostLink = NetBridge({ it() }, host)
        host.link = hostLink
        hostLink.fenProvider = { host.game.toFen() }
        hostLink.startHost(port, "host")

        val guestLink = NetBridge({ it() }, guest)
        guest.link = guestLink
        guestLink.startClient("127.0.0.1", port)

        assertTrue("host must accept", host.hello.await(8, TimeUnit.SECONDS))
        assertTrue("guest must say hello", guest.hello.await(8, TimeUnit.SECONDS))

        // 1) host plays e2-e4 and broadcasts, like board.onMovePlayed does
        val e4 = host.game.allLegalMoves().first { it.from == 52 && it.to == 36 }
        host.game.makeMove(e4)
        hostLink.sendFen(host.game.toFen())
        assertTrue("guest must apply the host move", guest.lastFen.await(8, TimeUnit.SECONDS))
        assertNull(guest.badFen)
        assertEquals(host.game.toFen(), guest.game.toFen())

        // 2) guest answers e7-e5; the host validates, applies and re-broadcasts
        val e5 = guest.game.allLegalMoves().first { it.from == 12 && it.to == 28 }
        guest.game.makeMove(e5)
        guestLink.sendMove(e5.from, e5.to, e5.promo)

        val before = guest.applied.get()
        val deadline = System.currentTimeMillis() + 8000
        while (guest.applied.get() == before && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }

        assertEquals("both boards must agree", host.game.toFen(), guest.game.toFen())
        assertTrue(guest.applied.get() >= 2)

        hostLink.close()
        guestLink.close()
    }
}

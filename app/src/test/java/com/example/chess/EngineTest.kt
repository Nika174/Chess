package com.example.chess

import com.example.chess.ai.Engine
import com.example.chess.chess.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private fun position(fen: String): Game {
        val g = Game()
        assertTrue("bad FEN: $fen", g.setFromFen(fen))
        return g
    }

    @Test
    fun everyLevelReturnsALegalMoveQuickly() {
        for (level in 1..5) {
            val g = Game()
            val before = g.toFen()
            val started = System.currentTimeMillis()
            val move = Engine(level).chooseMove(g)
            val took = System.currentTimeMillis() - started
            assertNotNull("level $level returned no move", move)
            assertTrue(
                "level $level produced an illegal move",
                g.allLegalMoves().any { it.from == move!!.from && it.to == move.to }
            )
            assertEquals("engine must not mutate the caller's game", before, g.toFen())
            assertTrue("level $level took ${took}ms", took < 10000)
        }
    }

    @Test
    fun findsMateInOne() {
        val g = position("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        val move = Engine(3).chooseMove(g)
        assertNotNull(move)
        assertEquals("a1-a8 is the only mate", 56, move!!.from)
        assertEquals(0, move.to)

        g.makeMove(move)
        assertTrue("position must be checkmate", g.gameOver())
        assertTrue(g.inCheck(g.side))
    }

    @Test
    fun takesTheHangingQueen() {
        val g = position("4k3/8/8/4q3/8/8/8/4R1K1 w - - 0 1")
        val move = Engine(3).chooseMove(g)
        assertNotNull(move)
        assertEquals(60, move!!.from) // e1 rook
        assertEquals(28, move.to)     // e5 queen
    }

    @Test
    fun doesNotGiveAwayItsQueenForFree() {
        // white may take a pawn on d5, but the queen on d1 would then be lost to ...Qxd1? no:
        // black queen on d8 and pawn d5 defended by the c6 pawn -> Qxd5 loses the queen
        val g = position("3qk3/8/2p5/3p4/8/8/8/3QK3 w - - 0 1")
        val move = Engine(4).chooseMove(g)
        assertNotNull(move)
        assertTrue("engine must not play Qxd5 into a defended pawn", !(move!!.from == 59 && move.to == 27))
    }

    @Test
    fun takesTheMoreValuablePiece() {
        // white queen d1 may take the black rook d5: 500 points beat anything else
        val g = position("4k3/8/8/3r4/8/8/8/3QK3 w - - 0 1")
        val move = Engine(3).chooseMove(g)
        assertNotNull(move)
        assertEquals(59, move!!.from)
        assertEquals(27, move.to)
    }
}

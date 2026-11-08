package com.example.chess

import com.example.chess.chess.Game
import com.example.chess.chess.KING
import com.example.chess.chess.WHITE
import com.example.chess.chess.pieceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reference checks for the rules engine. perft numbers are the standard ones,
 * so any regression in move generation fails the build.
 */
class GameTest {

    private fun perft(game: Game, depth: Int): Long {
        if (depth == 0) return 1
        var total = 0L
        for (m in game.allLegalMoves()) {
            game.makeMove(m)
            total += perft(game, depth - 1)
            game.undo()
        }
        return total
    }

    @Test
    fun initialPositionPerft() {
        assertEquals(20L, perft(Game(), 1))
        assertEquals(400L, perft(Game(), 2))
        assertEquals(8902L, perft(Game(), 3))
    }

    @Test
    fun kiwipetePerft() {
        val g = Game()
        assertTrue(g.setFromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"))
        assertEquals(48, g.allLegalMoves().size)
        assertEquals(2039L, perft(g, 2))
    }

    @Test
    fun rookMayCaptureTheCheckingBishop() {
        val g = Game()
        assertTrue(g.setFromFen("4k3/8/8/8/1b6/8/8/1R2K3 w - - 0 1"))
        assertTrue(g.inCheck(WHITE))
        val capture = g.allLegalMoves().any { it.from == 57 && it.to == 33 }
        assertTrue("b1xb4 must be legal while in check", capture)
    }

    @Test
    fun blackPawnCapturesDiagonally() {
        val g = Game()
        assertTrue(g.setFromFen("4k3/8/8/8/4p3/5P2/8/4K3 b - - 0 1"))
        val capture = g.allLegalMoves().any { it.from == 36 && it.to == 45 }
        assertTrue("e4xf3 must be legal for black", capture)
    }

    @Test
    fun enPassantWorks() {
        val g = Game()
        assertTrue(g.setFromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"))
        val ep = g.allLegalMoves().firstOrNull { it.from == 28 && it.to == 19 }
        assertNotNull("e5xd6 en passant must be legal", ep)
        g.makeMove(ep!!)
        assertEquals(0, g.pieceAt(27)) // the d5 pawn is gone
    }

    @Test
    fun fenRoundTrip() {
        val g = Game()
        val fen = g.toFen()
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", fen)
        val copy = Game()
        assertTrue(copy.setFromFen(fen))
        assertEquals(fen, copy.toFen())
    }

    @Test
    fun fenRoundTripAfterMoves() {
        val g = Game()
        g.makeMove(g.allLegalMoves().first { it.from == 52 && it.to == 36 }) // e2-e4
        val fen = g.toFen()
        val copy = Game()
        assertTrue(copy.setFromFen(fen))
        assertEquals(fen, copy.toFen())
        assertEquals(0, copy.capturedByWhite.size + copy.capturedByBlack.size)
    }

    @Test
    fun castlingMovesTheRook() {
        val g = Game()
        assertTrue(g.setFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        val castle = g.allLegalMoves().first { it.from == 60 && it.to == 62 }
        g.makeMove(castle)
        assertEquals(KING, pieceType(g.pieceAt(62)))
        assertEquals(4, pieceType(g.pieceAt(61))) // rook on f1
    }

    @Test
    fun undoRestoresCaptures() {
        val g = Game()
        assertTrue(g.setFromFen("4k3/8/8/8/8/8/4q3/4K3 w - - 0 1"))
        // white king takes the black queen
        val take = g.allLegalMoves().firstOrNull { it.from == 60 && it.to == 52 }
        assertNotNull(take)
        g.makeMove(take!!)
        assertEquals(1, g.capturedByWhite.size)
        g.undo()
        assertEquals(0, g.capturedByWhite.size)
    }
}

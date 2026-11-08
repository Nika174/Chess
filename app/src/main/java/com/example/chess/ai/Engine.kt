package com.example.chess.ai

import com.example.chess.chess.Game
import com.example.chess.chess.KING
import com.example.chess.chess.Move
import com.example.chess.chess.WHITE
import com.example.chess.chess.pieceColor
import com.example.chess.chess.pieceType
import kotlin.math.max
import kotlin.random.Random

/**
 * Small negamax engine with alpha-beta pruning, MVV-LVA ordering, a shallow
 * capture search and a time budget. Levels 1-5 trade depth for playing strength.
 *
 * The search runs on a private copy of the position, so the caller's Game is never mutated.
 */
class Engine(
    private val level: Int,
    private val random: Random = Random.Default
) {

    companion object {
        val LEVEL_NAMES = arrayOf("Новичок", "Лёгкий", "Средний", "Сложный", "Эксперт")

        private const val MATE = 100_000

        /** indexed by piece type: 0 empty, 1 pawn ... 6 king */
        private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20_000)

        // tables are written with rank 8 first, from white's point of view
        private val PAWN_PST = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0
        )

        private val KNIGHT_PST = intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
        )

        private val BISHOP_PST = intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
        )

        private val ROOK_PST = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0
        )

        private val QUEEN_PST = intArrayOf(
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20
        )

        private val KING_PST = intArrayOf(
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20
        )

        private fun pst(type: Int, sq: Int, white: Boolean): Int {
            val index = if (white) sq else (56 - (sq / 8) * 8) + (sq % 8)
            return when (type) {
                1 -> PAWN_PST[index]
                2 -> KNIGHT_PST[index]
                3 -> BISHOP_PST[index]
                4 -> ROOK_PST[index]
                5 -> QUEEN_PST[index]
                6 -> KING_PST[index]
                else -> 0
            }
        }
    }

    private var deadline = 0L
    private var nodes = 0L

    private fun maxDepth(): Int = when (level) {
        1 -> 1
        2 -> 2
        3 -> 3
        4 -> 4
        else -> 5
    }

    private fun budgetMs(): Long = when (level) {
        1 -> 250
        2 -> 450
        3 -> 900
        4 -> 1_600
        else -> 2_600
    }

    /** Best move for the side to move, or null when the game is over. */
    fun chooseMove(position: Game): Move? {
        val work = Game()
        if (!work.setFromFen(position.toFen())) return null
        val roots = work.allLegalMoves()
        if (roots.isEmpty()) return null

        nodes = 0
        deadline = System.currentTimeMillis() + budgetMs()

        var best: Move = roots.first()
        val noise = when (level) {
            1 -> 130
            2 -> 45
            else -> 0
        }

        for (depth in 1..maxDepth()) {
            val scored = ArrayList<Pair<Move, Int>>(roots.size)
            var alpha = -MATE
            for (m in orderMoves(work, roots)) {
                work.makeMove(m)
                var score = -negamax(work, depth - 1, -MATE, -alpha, 1)
                work.undo()
                if (noise > 0) score += random.nextInt(-noise, noise + 1)
                scored.add(m to score)
                if (score > alpha) alpha = score
                if (System.currentTimeMillis() > deadline) break
            }
            if (scored.isNotEmpty()) {
                val top = scored.maxOf { it.second }
                val pool = if (noise > 0) scored.filter { it.second >= top - noise } else scored.filter { it.second == top }
                best = pool[random.nextInt(pool.size)].first
            }
            if (System.currentTimeMillis() > deadline) break
        }
        return best
    }

    private fun negamax(g: Game, depth: Int, alpha: Int, beta: Int, ply: Int): Int {
        nodes++
        if (nodes and 2047L == 0L && System.currentTimeMillis() > deadline) return evaluate(g)

        val moves = g.allLegalMoves()
        if (moves.isEmpty()) return if (g.inCheck(g.side)) -MATE + ply else 0
        if (depth <= 0) return quiesce(g, alpha, beta, 0)

        var a = alpha
        for (m in orderMoves(g, moves)) {
            g.makeMove(m)
            val score = -negamax(g, depth - 1, -beta, -a, ply + 1)
            g.undo()
            if (score >= beta) return beta
            if (score > a) a = score
        }
        return a
    }

    /** capture-only search: stops the engine from leaving pieces hanging at the horizon */
    private fun quiesce(g: Game, alpha: Int, beta: Int, depthLeft: Int): Int {
        val stand = evaluate(g)
        if (depthLeft >= 4) return stand
        var a = max(alpha, stand)
        if (a >= beta) return beta

        val tactical = g.allLegalMoves()
            .filter { it.ep || it.promo != 0 || g.pieceAt(it.to) != 0 }
            .sortedByDescending { captureScore(g, it) }

        for (m in tactical) {
            g.makeMove(m)
            val score = -quiesce(g, -beta, -a, depthLeft + 1)
            g.undo()
            if (score >= beta) return beta
            if (score > a) a = score
        }
        return a
    }

    private fun evaluate(g: Game): Int {
        var score = 0
        for (sq in 0..63) {
            val p = g.pieceAt(sq)
            if (p == 0) continue
            val type = pieceType(p)
            val white = pieceColor(p) == WHITE
            val v = VALUE[type] + pst(type, sq, white)
            score += if (white) v else -v
        }
        return if (g.side == WHITE) score else -score
    }

    private fun orderMoves(g: Game, moves: List<Move>): List<Move> =
        moves.sortedByDescending { captureScore(g, it) }

    private fun captureScore(g: Game, m: Move): Int {
        var score = 0
        val victim = g.pieceAt(m.to)
        if (victim != 0) {
            val attacker = g.pieceAt(m.from)
            score += 10 * VALUE[pieceType(victim)] - VALUE[pieceType(attacker)]
        }
        if (m.promo != 0) score += VALUE[m.promo]
        if (m.ep) score += 100
        return score
    }

    /** quick material read for the UI ("you are +3") */
    fun materialBalance(position: Game): Int {
        var score = 0
        for (sq in 0..63) {
            val p = position.pieceAt(sq)
            if (p == 0) continue
            if (pieceType(p) == KING) continue
            val v = VALUE[pieceType(p)]
            score += if (pieceColor(p) == WHITE) v else -v
        }
        return score
    }
}

package com.example.chess.chess

const val WHITE = 0
const val BLACK = 1

const val PAWN = 1
const val KNIGHT = 2
const val BISHOP = 3
const val ROOK = 4
const val QUEEN = 5
const val KING = 6

/** piece encoding: type | color<<3 ; 0 = empty */
fun pieceType(p: Int) = p and 7
fun pieceColor(p: Int) = p shr 3 and 1
fun makePiece(type: Int, color: Int) = type or (color shl 3)

data class Move(
    val from: Int,
    val to: Int,
    val promo: Int = 0,
    val castle: Boolean = false,
    val ep: Boolean = false
)

class Game {

    var board = IntArray(64)
        private set
    var side = WHITE
        private set
    /** bits: 1 = white short, 2 = white long, 4 = black short, 8 = black long */
    var castling = 0
        private set
    var epSquare = -1
        private set
    var lastMove: Move? = null
        private set

    private val history = ArrayList<Snapshot>()
    val capturedByWhite = ArrayList<Int>()
    val capturedByBlack = ArrayList<Int>()
    var moveNumber = 1
        private set

    private data class Snapshot(
        val board: IntArray,
        val side: Int,
        val castling: Int,
        val ep: Int,
        val last: Move?,
        val capW: List<Int>,
        val capB: List<Int>,
        val moveNo: Int
    )

    init {
        reset()
    }

    fun reset() {
        board = IntArray(64)
        val back = intArrayOf(ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK)
        for (f in 0..7) {
            board[f] = makePiece(back[f], BLACK)
            board[8 + f] = makePiece(PAWN, BLACK)
            board[48 + f] = makePiece(PAWN, WHITE)
            board[56 + f] = makePiece(back[f], WHITE)
        }
        side = WHITE
        castling = 0b1111
        epSquare = -1
        lastMove = null
        history.clear()
        capturedByWhite.clear()
        capturedByBlack.clear()
        moveNumber = 1
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val s = history.removeAt(history.size - 1)
        board = s.board
        side = s.side
        castling = s.castling
        epSquare = s.ep
        lastMove = s.last
        capturedByWhite.clear()
        capturedByWhite.addAll(s.capW)
        capturedByBlack.clear()
        capturedByBlack.addAll(s.capB)
        moveNumber = s.moveNo
        return true
    }

    fun canUndo() = history.isNotEmpty()

    fun pieceAt(sq: Int) = board[sq]

    // ---------------- move generation ----------------

    private val knightDeltas = intArrayOf(-2, -1, -2, 1, -1, -2, -1, 2, 1, -2, 1, 2, 2, -1, 2, 1)
    private val diag = intArrayOf(-1, -1, -1, 1, 1, -1, 1, 1)
    private val kingDeltas = intArrayOf(-1, -1, -1, 0, -1, 1, 0, -1, 0, 1, 1, -1, 1, 0, 1, 1)
    private val orth = intArrayOf(-1, 0, 1, 0, 0, -1, 0, 1)

    private fun pseudoMoves(sq: Int): MutableList<Move> {
        val p = board[sq]
        val res = mutableListOf<Move>()
        if (p == 0) return res
        val t = pieceType(p)
        val c = pieceColor(p)
        val r = sq / 8
        val f = sq % 8

        fun push(tr: Int, tf: Int) {
            if (tr !in 0..7 || tf !in 0..7) return
            val to = tr * 8 + tf
            val q = board[to]
            if (q == 0 || pieceColor(q) != c) res.add(Move(sq, to))
        }

        when (t) {
            PAWN -> {
                val dir = if (c == WHITE) -1 else 1
                val startRow = if (c == WHITE) 6 else 1
                val promoRow = if (c == WHITE) 0 else 7
                val oneR = r + dir
                if (oneR in 0..7) {
                    val one = oneR * 8 + f
                    if (board[one] == 0) {
                        if (oneR == promoRow) for (pr in intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT))
                            res.add(Move(sq, one, pr))
                        else res.add(Move(sq, one))

                        val twoR = r + 2 * dir
                        if (r == startRow && board[twoR * 8 + f] == 0) res.add(Move(sq, twoR * 8 + f))
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val tf = f + df
                        if (tf !in 0..7) continue
                        val to = oneR * 8 + tf
                        val q = board[to]
                        if (q != 0 && pieceColor(q) != c) {
                            if (oneR == promoRow) for (pr in intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT))
                                res.add(Move(sq, to, pr))
                            else res.add(Move(sq, to))
                        } else if (to == epSquare && q == 0) {
                            res.add(Move(sq, to, 0, false, true))
                        }
                    }
                }
            }

            KNIGHT -> {
                for (i in 0 until 8) push(r + knightDeltas[i * 2], f + knightDeltas[i * 2 + 1])
            }

            BISHOP -> slide(sq, c, diag, res)
            ROOK -> slide(sq, c, orth, res)
            QUEEN -> {
                slide(sq, c, diag, res)
                slide(sq, c, orth, res)
            }

            KING -> {
                for (i in 0 until 8) push(r + kingDeltas[i * 2], f + kingDeltas[i * 2 + 1])
                // castling
                if (c == WHITE && sq == 60) {
                    if (castling and 1 != 0 && board[61] == 0 && board[62] == 0 &&
                        board[63] == makePiece(ROOK, WHITE)) res.add(Move(sq, 62, 0, true))
                    if (castling and 2 != 0 && board[59] == 0 && board[58] == 0 && board[57] == 0 &&
                        board[56] == makePiece(ROOK, WHITE)) res.add(Move(sq, 58, 0, true))
                } else if (c == BLACK && sq == 4) {
                    if (castling and 4 != 0 && board[5] == 0 && board[6] == 0 &&
                        board[7] == makePiece(ROOK, BLACK)) res.add(Move(sq, 6, 0, true))
                    if (castling and 8 != 0 && board[3] == 0 && board[2] == 0 && board[1] == 0 &&
                        board[0] == makePiece(ROOK, BLACK)) res.add(Move(sq, 2, 0, true))
                }
            }
        }
        return res
    }

    private fun slide(sq: Int, c: Int, dirs: IntArray, res: MutableList<Move>) {
        val r0 = sq / 8
        val f0 = sq % 8
        for (i in 0 until 4) {
            var r = r0 + dirs[i * 2]
            var f = f0 + dirs[i * 2 + 1]
            while (r in 0..7 && f in 0..7) {
                val to = r * 8 + f
                val q = board[to]
                if (q == 0) {
                    res.add(Move(sq, to))
                } else {
                    if (pieceColor(q) != c) res.add(Move(sq, to))
                    break
                }
                r += dirs[i * 2]
                f += dirs[i * 2 + 1]
            }
        }
    }

    fun legalMovesFrom(sq: Int): List<Move> {
        if (sq !in 0..63) return emptyList()
        val p = board[sq]
        if (p == 0 || pieceColor(p) != side) return emptyList()
        val out = mutableListOf<Move>()
        for (m in pseudoMoves(sq)) {
            if (isLegalIgnoringCheck(m) && !leavesKingInCheck(m)) out.add(m)
        }
        return out
    }

    fun allLegalMoves(): List<Move> {
        val out = mutableListOf<Move>()
        for (sq in 0..63) {
            val p = board[sq]
            if (p == 0 || pieceColor(p) != side) continue
            for (m in pseudoMoves(sq)) {
                if (isLegalIgnoringCheck(m) && !leavesKingInCheck(m)) out.add(m)
            }
        }
        return out
    }

    /** castling through check is forbidden */
    private fun isLegalIgnoringCheck(m: Move): Boolean {
        if (!m.castle) return true
        val c = side
        val kingFrom = m.from
        val step = if (m.to > m.from) 1 else -1
        var sq = kingFrom
        while (sq != m.to) {
            if (isAttacked(sq, 1 - c)) return false
            sq += step
        }
        return !isAttacked(m.to, 1 - c)
    }

    private fun leavesKingInCheck(m: Move): Boolean {
        val saved = board
        val savedCastling = castling
        val savedEp = epSquare
        board = saved.copyOf()
        doMove(m)
        val bad = inCheck(pieceColor(saved[m.from]))
        board = saved
        castling = savedCastling
        epSquare = savedEp
        return bad
    }

    fun inCheck(color: Int): Boolean {
        val k = board.indexOfFirst { pieceType(it) == KING && pieceColor(it) == color }
        if (k < 0) return false
        return isAttacked(k, 1 - color)
    }

    fun isAttacked(sq: Int, byColor: Int): Boolean {
        val r0 = sq / 8
        val f0 = sq % 8

        // pawns
        val dir = if (byColor == WHITE) 1 else -1
        for (df in intArrayOf(-1, 1)) {
            val r = r0 + dir
            val f = f0 + df
            if (r in 0..7 && f in 0..7) {
                val p = board[r * 8 + f]
                if (pieceType(p) == PAWN && pieceColor(p) == byColor) return true
            }
        }
        // knights
        for (i in 0 until 8) {
            val r = r0 + knightDeltas[i * 2]
            val f = f0 + knightDeltas[i * 2 + 1]
            if (r in 0..7 && f in 0..7) {
                val p = board[r * 8 + f]
                if (pieceType(p) == KNIGHT && pieceColor(p) == byColor) return true
            }
        }
        // king
        for (i in 0 until 8) {
            val r = r0 + kingDeltas[i * 2]
            val f = f0 + kingDeltas[i * 2 + 1]
            if (r in 0..7 && f in 0..7) {
                val p = board[r * 8 + f]
                if (pieceType(p) == KING && pieceColor(p) == byColor) return true
            }
        }
        // sliders
        for (i in 0 until 4) {
            var r = r0 + diag[i * 2]
            var f = f0 + diag[i * 2 + 1]
            while (r in 0..7 && f in 0..7) {
                val p = board[r * 8 + f]
                if (p != 0) {
                    if (pieceColor(p) == byColor) {
                        val t = pieceType(p)
                        if (t == BISHOP || t == QUEEN) return true
                    }
                    break
                }
                r += diag[i * 2]
                f += diag[i * 2 + 1]
            }
        }
        for (i in 0..3) {
            var r = r0 + orth[i * 2]
            var f = f0 + orth[i * 2 + 1]
            while (r in 0..7 && f in 0..7) {
                val p = board[r * 8 + f]
                if (p != 0) {
                    if (pieceColor(p) == byColor) {
                        val t = pieceType(p)
                        if (t == ROOK || t == QUEEN) return true
                    }
                    break
                }
                r += orth[i * 2]
                f += orth[i * 2 + 1]
            }
        }
        return false
    }

    // ---------------- making moves ----------------

    fun makeMove(m: Move) {
        history.add(
            Snapshot(
                board.copyOf(), side, castling, epSquare, lastMove,
                ArrayList(capturedByWhite), ArrayList(capturedByBlack), moveNumber
            )
        )
        val victim = if (m.ep) {
            val capSq = if (pieceColor(board[m.from]) == WHITE) m.to + 8 else m.to - 8
            board[capSq]
        } else {
            board[m.to]
        }
        if (victim != 0) {
            if (pieceColor(board[m.from]) == WHITE) capturedByWhite.add(victim)
            else capturedByBlack.add(victim)
        }
        doMove(m)
        if (side == BLACK) moveNumber++
        lastMove = m
        side = 1 - side
    }

    private fun doMove(m: Move) {
        val p = board[m.from]
        val c = pieceColor(p)
        val t = pieceType(p)

        // en passant capture
        if (m.ep) {
            val capSq = if (c == WHITE) m.to + 8 else m.to - 8
            board[capSq] = 0
        }
        board[m.to] = if (m.promo != 0) makePiece(m.promo, c) else p
        board[m.from] = 0

        // castling rook move
        if (m.castle) {
            when (m.to) {
                62 -> { board[61] = board[63]; board[63] = 0 }
                58 -> { board[59] = board[56]; board[56] = 0 }
                6 -> { board[5] = board[7]; board[7] = 0 }
                2 -> { board[3] = board[0]; board[0] = 0 }
            }
        }

        // castling rights
        if (t == KING) {
            castling = if (c == WHITE) castling and 0b1100 else castling and 0b0011
        }
        if (m.from == 63 || m.to == 63) castling = castling and 1.inv()
        if (m.from == 56 || m.to == 56) castling = castling and 2.inv()
        if (m.from == 7 || m.to == 7) castling = castling and 4.inv()
        if (m.from == 0 || m.to == 0) castling = castling and 8.inv()

        // en passant square
        epSquare = if (t == PAWN && Math.abs(m.to - m.from) == 16) (m.from + m.to) / 2 else -1
    }

    // ---------------- status ----------------

    fun gameOver(): Boolean = allLegalMoves().isEmpty()

    fun statusText(): String {
        val sideName = if (side == WHITE) "белых" else "чёрных"
        val moves = allLegalMoves()
        if (moves.isEmpty()) {
            return if (inCheck(side)) {
                val winner = if (side == WHITE) "чёрных" else "белых"
                "Мат! Победа $winner"
            } else {
                "Пат — ничья"
            }
        }
        return if (inCheck(side)) "Шах! Ход $sideName" else "Ход $sideName"
    }

    fun kingSquare(color: Int): Int =
        board.indexOfFirst { pieceType(it) == KING && pieceColor(it) == color }

    /** Standard FEN — lets us reproduce an exact position for debugging. */
    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var empty = 0
            for (f in 0..7) {
                val p = board[r * 8 + f]
                if (p == 0) {
                    empty++
                    continue
                }
                if (empty > 0) {
                    sb.append(empty)
                    empty = 0
                }
                val ch = "pnbrqk"[pieceType(p) - 1]
                sb.append(if (pieceColor(p) == WHITE) ch.uppercaseChar() else ch)
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        sb.append(' ').append(if (side == WHITE) 'w' else 'b')
        var rights = ""
        if (castling and 1 != 0) rights += "K"
        if (castling and 2 != 0) rights += "Q"
        if (castling and 4 != 0) rights += "k"
        if (castling and 8 != 0) rights += "q"
        sb.append(' ').append(if (rights.isEmpty()) "-" else rights)
        sb.append(' ')
        if (epSquare >= 0) {
            sb.append(('a' + epSquare % 8)).append(8 - epSquare / 8)
        } else {
            sb.append('-')
        }
        sb.append(" 0 ").append(moveNumber)
        return sb.toString()
    }
}

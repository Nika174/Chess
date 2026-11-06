package com.example.chess.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.chess.chess.BISHOP
import com.example.chess.chess.Game
import com.example.chess.chess.KING
import com.example.chess.chess.KNIGHT
import com.example.chess.chess.Move
import com.example.chess.chess.PAWN
import com.example.chess.chess.QUEEN
import com.example.chess.chess.ROOK
import com.example.chess.chess.WHITE
import com.example.chess.chess.pieceColor
import com.example.chess.chess.pieceType
import kotlin.math.min

class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: Game = Game()
        set(value) {
            field = value
            selected = -1
            moves = emptyList()
            invalidate()
        }

    var flipped = false
        set(value) {
            field = value
            invalidate()
        }

    /** called when the player must pick a promotion piece: (from, to) -> choose */
    var onPromotion: ((List<Move>, (Move) -> Unit) -> Unit)? = null
    var onMove: (() -> Unit)? = null

    private var selected = -1
    private var moves: List<Move> = emptyList()

    private val d = resources.displayMetrics.density
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()

    private val lightColor = 0xFFF0D9B5.toInt()
    private val darkColor = 0xFFB58863.toInt()
    private val selColor = 0x66F6C445
    private val moveColor = 0x55000000
    private val lastColor = 0x44E8C03A
    private val checkColor = 0x77E0453E

    init {
        setBackgroundColor(0xFF12100E.toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val side = min(w, h)
        setMeasuredDimension(side, side)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        cell = w / 8f
        boardLeft = 0f
        boardTop = 0f
    }

    private fun sqRect(sq: Int): RectF {
        val file = if (flipped) 7 - sq % 8 else sq % 8
        val rank = if (flipped) 7 - sq / 8 else sq / 8
        val x = boardLeft + file * cell
        val y = boardTop + rank * cell
        rect.set(x, y, x + cell, y + cell)
        return rect
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cell = width / 8f

        // squares
        for (sq in 0..63) {
            val r = sqRect(sq)
            paint.style = Paint.Style.FILL
            paint.color = if ((sq / 8 + sq % 8) % 2 == 0) lightColor else darkColor
            canvas.drawRect(r, paint)
        }

        // last move
        game.lastMove?.let { m ->
            paint.color = lastColor
            canvas.drawRect(sqRect(m.from), paint)
            canvas.drawRect(sqRect(m.to), paint)
        }

        // selected
        if (selected >= 0) {
            paint.color = selColor
            canvas.drawRect(sqRect(selected), paint)
        }

        // check highlight
        if (game.inCheck(game.side)) {
            val k = game.kingSquare(game.side)
            if (k >= 0) {
                paint.color = checkColor
                canvas.drawCircle(sqRect(k).centerX(), sqRect(k).centerY(), cell * 0.46f, paint)
            }
        }

        // pieces
        for (sq in 0..63) {
            val p = game.pieceAt(sq)
            if (p == 0) continue
            drawPiece(canvas, sqRect(sq), p)
        }

        // legal move dots
        paint.style = Paint.Style.FILL
        for (m in moves) {
            val r = sqRect(m.to)
            if (game.pieceAt(m.to) != 0) {
                stroke.color = moveColor
                stroke.strokeWidth = cell * 0.07f
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.42f, stroke)
            } else {
                paint.color = moveColor
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.13f, paint)
            }
        }
    }

    // ---------------- vector pieces ----------------

    private fun drawPiece(canvas: Canvas, r: RectF, piece: Int) {
        val white = pieceColor(piece) == WHITE
        val fill = if (white) 0xFFFFF6E5.toInt() else 0xFF241E19.toInt()
        val outline = if (white) 0xFF2A2018.toInt() else 0xFFE8DCC8.toInt()

        val s = cell
        val cx = r.centerX()
        val cy = r.centerY()
        val base = cy + s * 0.30f
        val top = cy - s * 0.30f

        paint.style = Paint.Style.FILL
        paint.color = fill
        stroke.color = outline
        stroke.strokeWidth = s * 0.045f

        path.reset()
        when (pieceType(piece)) {
            PAWN -> {
                path.addCircle(cx, cy - s * 0.13f, s * 0.115f, Path.Direction.CW)
                path.addRoundRect(
                    RectF(cx - s * 0.15f, cy - s * 0.08f, cx + s * 0.15f, cy + s * 0.20f),
                    s * 0.06f, s * 0.06f, Path.Direction.CW
                )
                path.addRoundRect(
                    RectF(cx - s * 0.22f, cy + s * 0.18f, cx + s * 0.22f, cy + s * 0.29f),
                    s * 0.05f, s * 0.05f, Path.Direction.CW
                )
            }

            ROOK -> {
                path.addRoundRect(
                    RectF(cx - s * 0.20f, cy - s * 0.05f, cx + s * 0.20f, cy + s * 0.22f),
                    s * 0.03f, s * 0.03f, Path.Direction.CW
                )
                path.addRect(cx - s * 0.22f, cy - s * 0.24f, cx + s * 0.22f, cy - s * 0.05f, Path.Direction.CW)
                path.addRect(cx - s * 0.22f, cy - s * 0.30f, cx - s * 0.12f, cy - s * 0.18f, Path.Direction.CW)
                path.addRect(cx - s * 0.05f, cy - s * 0.30f, cx + s * 0.05f, cy - s * 0.18f, Path.Direction.CW)
                path.addRect(cx + s * 0.12f, cy - s * 0.30f, cx + s * 0.22f, cy - s * 0.18f, Path.Direction.CW)
                path.addRoundRect(
                    RectF(cx - s * 0.25f, cy + s * 0.20f, cx + s * 0.25f, cy + s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }

            KNIGHT -> {
                path.moveTo(cx - s * 0.16f, cy + s * 0.22f)
                path.lineTo(cx - s * 0.10f, cy - s * 0.02f)
                path.lineTo(cx - s * 0.20f, cy - s * 0.12f)
                path.lineTo(cx - s * 0.06f, cy - s * 0.26f)
                path.lineTo(cx + s * 0.04f, cy - s * 0.18f)
                path.lineTo(cx + s * 0.18f, cy - s * 0.06f)
                path.lineTo(cx + s * 0.14f, cy + s * 0.10f)
                path.lineTo(cx + s * 0.10f, cy + s * 0.22f)
                path.close()
                path.addRoundRect(
                    RectF(cx - s * 0.22f, cy + s * 0.20f, cx + s * 0.22f, cy + s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }

            BISHOP -> {
                path.addCircle(cx, cy - s * 0.20f, s * 0.07f, Path.Direction.CW)
                path.moveTo(cx, cy - s * 0.34f)
                path.lineTo(cx + s * 0.17f, cy + s * 0.10f)
                path.lineTo(cx - s * 0.17f, cy + s * 0.10f)
                path.close()
                path.addRoundRect(
                    RectF(cx - s * 0.22f, cy + s * 0.18f, cx + s * 0.22f, cy + s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }

            QUEEN -> {
                path.addCircle(cx - s * 0.18f, cy - s * 0.24f, s * 0.055f, Path.Direction.CW)
                path.addCircle(cx, cy - s * 0.31f, s * 0.06f, Path.Direction.CW)
                path.addCircle(cx + s * 0.18f, cy - s * 0.24f, s * 0.055f, Path.Direction.CW)
                path.moveTo(cx - s * 0.20f, cy - s * 0.20f)
                path.lineTo(cx - s * 0.10f, cy + s * 0.12f)
                path.lineTo(cx + s * 0.10f, cy + s * 0.12f)
                path.lineTo(cx + s * 0.20f, cy - s * 0.20f)
                path.close()
                path.addRoundRect(
                    RectF(cx - s * 0.23f, cy + s * 0.18f, cx + s * 0.23f, cy + s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }

            KING -> {
                path.addRect(cx - s * 0.035f, cy - s * 0.36f, cx + s * 0.035f, cy - s * 0.14f, Path.Direction.CW)
                path.addRect(cx - s * 0.11f, cy - s * 0.28f, cx + s * 0.11f, cy - s * 0.21f, Path.Direction.CW)
                path.moveTo(cx - s * 0.19f, cy - s * 0.10f)
                path.lineTo(cx - s * 0.11f, cy + s * 0.12f)
                path.lineTo(cx + s * 0.11f, cy + s * 0.12f)
                path.lineTo(cx + s * 0.19f, cy - s * 0.10f)
                path.close()
                path.addRoundRect(
                    RectF(cx - s * 0.23f, cy + s * 0.18f, cx + s * 0.23f, cy + s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }
        }
        canvas.drawPath(path, paint)
        canvas.drawPath(path, stroke)
    }

    // ---------------- input ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val file = (event.x / cell).toInt().coerceIn(0, 7)
        val rank = (event.y / cell).toInt().coerceIn(0, 7)
        val sq = if (flipped) (7 - rank) * 8 + (7 - file) else rank * 8 + file
        onSquare(sq)
        return true
    }

    private fun onSquare(sq: Int) {
        val p = game.pieceAt(sq)

        if (selected >= 0) {
            val candidates = moves.filter { it.to == sq }
            if (candidates.isNotEmpty()) {
                if (candidates.size > 1 && candidates.all { it.promo != 0 }) {
                    onPromotion?.invoke(candidates) { chosen ->
                        play(chosen)
                    }
                } else {
                    play(candidates.first())
                }
                return
            }
        }

        if (p != 0 && pieceColor(p) == game.side) {
            selected = sq
            moves = game.legalMovesFrom(sq)
        } else {
            selected = -1
            moves = emptyList()
        }
        invalidate()
    }

    private fun play(m: Move) {
        game.makeMove(m)
        selected = -1
        moves = emptyList()
        invalidate()
        onMove?.invoke()
    }

    fun clearSelection() {
        selected = -1
        moves = emptyList()
        invalidate()
    }
}

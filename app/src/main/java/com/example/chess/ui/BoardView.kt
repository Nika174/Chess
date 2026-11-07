package com.example.chess.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.chess.chess.Game
import com.example.chess.chess.Move
import com.example.chess.chess.pieceColor
import kotlin.math.abs
import kotlin.math.min

class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: Game = Game()
        set(value) {
            field = value
            resetTouchState()
            invalidate()
        }

    var flipped = false
        set(value) {
            field = value
            invalidate()
        }

    /** (promotion candidates, apply chosen move) */
    var onPromotion: ((List<Move>, (Move) -> Unit) -> Unit)? = null
    var onMove: (() -> Unit)? = null

    private var selected = -1
    private var moves: List<Move> = emptyList()

    private var dragFrom = -1
    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var hoverSq = -1

    private val d = resources.displayMetrics.density
    private var cell = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val rect2 = RectF()

    private val lightColor = 0xFFF0D9B5.toInt()
    private val darkColor = 0xFFB58863.toInt()
    private val selColor = 0x66F6C445
    private val moveColor = 0x55000000
    private val lastColor = 0x44E8C03A
    private val checkColor = 0x77E0453E
    private val hoverColor = 0x5540C070
    private val shadowColor = 0x33000000

    init {
        setBackgroundColor(0xFF12100E.toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(min(w, h), min(w, h))
    }

    override fun onSizeChanged(w: Int, h: Int, o: Int, oh: Int) {
        super.onSizeChanged(w, h, o, oh)
        cell = w / 8f
    }

    private fun squareRect(sq: Int, out: RectF): RectF {
        val file = if (flipped) 7 - sq % 8 else sq % 8
        val rank = if (flipped) 7 - sq / 8 else sq / 8
        val x = file * cell
        val y = rank * cell
        out.set(x, y, x + cell, y + cell)
        return out
    }

    private fun sqAt(x: Float, y: Float): Int {
        if (cell <= 0f) return -1
        val file = (x / cell).toInt().coerceIn(0, 7)
        val rank = (y / cell).toInt().coerceIn(0, 7)
        return if (flipped) (7 - rank) * 8 + (7 - file) else rank * 8 + file
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cell = width / 8f

        paint.style = Paint.Style.FILL
        for (sq in 0..63) {
            squareRect(sq, rect)
            paint.color = if ((sq / 8 + sq % 8) % 2 == 0) lightColor else darkColor
            canvas.drawRect(rect, paint)
        }

        game.lastMove?.let { m ->
            paint.color = lastColor
            canvas.drawRect(squareRect(m.from, rect), paint)
            canvas.drawRect(squareRect(m.to, rect), paint)
        }

        if (selected >= 0) {
            paint.color = selColor
            canvas.drawRect(squareRect(selected, rect), paint)
        }

        if (dragging && hoverSq >= 0) {
            paint.color = hoverColor
            canvas.drawRect(squareRect(hoverSq, rect), paint)
        }

        if (game.inCheck(game.side)) {
            val k = game.kingSquare(game.side)
            if (k >= 0) {
                val r = squareRect(k, rect)
                paint.color = checkColor
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.46f, paint)
            }
        }

        for (sq in 0..63) {
            val p = game.pieceAt(sq)
            if (p == 0) continue
            if (dragging && sq == dragFrom) continue
            val r = squareRect(sq, rect)
            PieceArt.draw(canvas, r.centerX(), r.centerY(), cell, p)
        }

        for (m in moves) {
            val r = squareRect(m.to, rect2)
            if (game.pieceAt(m.to) != 0) {
                stroke.color = moveColor
                stroke.strokeWidth = cell * 0.07f
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.42f, stroke)
            } else {
                paint.color = moveColor
                paint.style = Paint.Style.FILL
                canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.13f, paint)
            }
        }

        if (dragging && dragFrom >= 0) {
            val p = game.pieceAt(dragFrom)
            if (p != 0) {
                paint.color = shadowColor
                canvas.drawCircle(dragX, dragY + cell * 0.10f, cell * 0.30f, paint)
                PieceArt.draw(canvas, dragX, dragY, cell * 1.12f, p)
            }
        }
    }

    // ---------------- input: drag&drop AND tap-tap ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val sq = sqAt(event.x, event.y)
                if (sq < 0) return false
                dragX = event.x
                dragY = event.y
                dragging = false
                hoverSq = -1
                dragFrom = -1

                val p = game.pieceAt(sq)
                if (p != 0 && pieceColor(p) == game.side) {
                    dragFrom = sq
                    selected = sq
                    moves = game.legalMovesFrom(sq)
                    invalidate()
                } else if (selected >= 0 && moves.any { it.to == sq }) {
                    attemptMove(sq)
                } else {
                    selected = -1
                    moves = emptyList()
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragFrom < 0) return true
                if (abs(event.x - dragX) > d * 6 || abs(event.y - dragY) > d * 6) dragging = true
                dragX = event.x
                dragY = event.y
                if (dragging) {
                    hoverSq = sqAt(event.x, event.y)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val from = dragFrom
                val wasDragging = dragging
                val sq = sqAt(event.x, event.y)
                dragFrom = -1
                dragging = false
                hoverSq = -1

                if (from >= 0 && wasDragging && sq >= 0 && sq != from) {
                    attemptMove(sq)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetTouchState()
                invalidate()
                return true
            }
        }
        return true
    }

    /** moves the selected piece to [to] if that destination is legal */
    private fun attemptMove(to: Int): Boolean {
        val candidates = moves.filter { it.to == to }
        if (candidates.isEmpty()) return false
        if (candidates.size > 1 && candidates.all { it.promo != 0 }) {
            onPromotion?.invoke(candidates) { chosen -> play(chosen) }
            return true
        }
        play(candidates.first())
        return true
    }

    private fun play(m: Move) {
        game.makeMove(m)
        resetTouchState()
        invalidate()
        onMove?.invoke()
    }

    private fun resetTouchState() {
        selected = -1
        moves = emptyList()
        dragFrom = -1
        dragging = false
        hoverSq = -1
    }

    fun clearSelection() {
        resetTouchState()
        invalidate()
    }
}

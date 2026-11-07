package com.example.chess.ui

import android.content.Context
import android.animation.ValueAnimator
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
            resetTouchState()
            invalidate()
        }

    /** (promotion candidates, apply chosen move) */
    var onPromotion: ((List<Move>, (Move) -> Unit) -> Unit)? = null
    var onMove: (() -> Unit)? = null

    /** fires with the move that was just played locally */
    var onMovePlayed: ((Move) -> Unit)? = null

    /** -1 = both sides may move (hot-seat); otherwise only this colour may touch pieces */
    var allowedSide = -1

    /** when set and it returns true, the view does NOT apply the move locally (network guest) */
    var interceptMove: ((Move) -> Boolean)? = null

    private var selected = -1
    private var moves: List<Move> = emptyList()
    private var movesVersion = -1

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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000
        textAlign = Paint.Align.RIGHT
    }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33FFFFFF
    }

    private var animating = false
    private var animFrom = -1
    private var animTo = -1
    private var animPiece = 0
    private var animFrac = 1f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 150
        addUpdateListener {
            animFrac = it.animatedValue as Float
            invalidate()
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                animating = false
                invalidate()
            }
        })
    }

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

        drawCoordinates(canvas)

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
            if (animating && sq == animTo) continue
            val r = squareRect(sq, rect)
            PieceArt.draw(canvas, r.centerX(), r.centerY(), cell, p)
        }

        for (m in currentMoves()) {
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

        if (animating && animPiece != 0) {
            val a = squareRect(animFrom, rect)
            val ax = a.centerX()
            val ay = a.centerY()
            val b = squareRect(animTo, rect2)
            PieceArt.draw(
                canvas,
                ax + (b.centerX() - ax) * animFrac,
                ay + (b.centerY() - ay) * animFrac,
                cell,
                animPiece
            )
        }

        framePaint.strokeWidth = cell * 0.04f
        rect2.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect2, cell * 0.05f, cell * 0.05f, framePaint)

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
                if (allowedSide >= 0 && game.side != allowedSide) return true
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
                    movesVersion = game.version
                    invalidate()
                } else if (selected >= 0 && currentMoves().any { it.to == sq }) {
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
        val candidates = currentMoves().filter { it.to == to }
        if (candidates.isEmpty()) return false
        if (candidates.size > 1 && candidates.all { it.promo != 0 }) {
            onPromotion?.invoke(candidates) { chosen -> play(chosen) }
            return true
        }
        play(candidates.first())
        return true
    }

    private fun play(m: Move) {
        if (interceptMove?.invoke(m) == true) return
        val moving = game.pieceAt(m.from)
        game.makeMove(m)
        if (moving != 0 && m.from != m.to) {
            animFrom = m.from
            animTo = m.to
            animPiece = moving
            animFrac = 0f
            animating = true
            animator.cancel()
            animator.start()
        }
        resetTouchState()
        invalidate()
        onMove?.invoke()
        onMovePlayed?.invoke(m)
    }

    private fun drawCoordinates(canvas: Canvas) {
        if (cell < 42f) return
        textPaint.textSize = cell * 0.17f
        textPaint.textAlign = Paint.Align.RIGHT
        for (d in 0..7) {
            val file = if (flipped) 7 - d else d
            val label = ('a' + file).toString()
            textPaint.color = if ((7 + file) % 2 == 0) 0x55000000 else 0x55FFFFFF
            canvas.drawText(label, d * cell + cell * 0.86f, 8 * cell - cell * 0.08f, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
        for (r in 0..7) {
            val rank = if (flipped) r + 1 else 8 - r
            textPaint.color = if ((r + 0) % 2 == 0) 0x55000000 else 0x55FFFFFF
            canvas.drawText(rank.toString(), d0(), r * cell + cell * 0.22f, textPaint)
        }
    }

    private fun d0(): Float = cell * 0.08f

    /** legal moves of the selected piece, recomputed whenever the position changed */
    private fun currentMoves(): List<Move> {
        if (selected < 0) return emptyList()
        val p = game.pieceAt(selected)
        if (p == 0 || pieceColor(p) != game.side) {
            resetTouchState()
            invalidate()
            return emptyList()
        }
        if (movesVersion != game.version) {
            moves = game.legalMovesFrom(selected)
            movesVersion = game.version
        }
        return moves
    }

    private fun resetTouchState() {
        selected = -1
        movesVersion = -1
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

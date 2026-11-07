package com.example.chess.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.chess.chess.Game
import com.example.chess.chess.KING
import com.example.chess.chess.PAWN
import com.example.chess.chess.pieceType

/** Two strips: what white has captured (left) and what black has captured (right). */
class CapturedView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var game: Game? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2E2A24.toInt()
        strokeWidth = resources.displayMetrics.density
    }

    private val order = mapOf(KING to 0, 5 to 1, 4 to 2, 3 to 3, 2 to 4, PAWN to 5)

    fun refresh() = invalidate()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = game ?: return
        val h = height.toFloat()
        if (h <= 0f) return

        val size = h * 0.86f
        val gap = size * 0.62f
        val byWhite = g.capturedByWhite.sortedBy { order[pieceType(it)] ?: 9 }
        val byBlack = g.capturedByBlack.sortedBy { order[pieceType(it)] ?: 9 }

        var x = size / 2f
        for (p in byWhite) {
            PieceArt.draw(canvas, x, h / 2f, size, p)
            x += gap
        }

        x = width - size / 2f
        for (p in byBlack) {
            PieceArt.draw(canvas, x, h / 2f, size, p)
            x -= gap
        }

        // small divider in the middle
        canvas.drawLine(
            width / 2f, h * 0.2f, width / 2f, h * 0.8f, divider
        )
    }
}

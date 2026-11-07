package com.example.chess.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.chess.chess.BISHOP
import com.example.chess.chess.KING
import com.example.chess.chess.KNIGHT
import com.example.chess.chess.PAWN
import com.example.chess.chess.QUEEN
import com.example.chess.chess.ROOK
import com.example.chess.chess.WHITE
import com.example.chess.chess.pieceColor
import com.example.chess.chess.pieceType

/** Shared vector art so the board and the "captured" strips look the same. */
object PieceArt {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    fun draw(canvas: Canvas, cx: Float, cy: Float, size: Float, piece: Int) {
        val white = pieceColor(piece) == WHITE
        fillPaint.color = if (white) 0xFFFFF6E5.toInt() else 0xFF241E19.toInt()
        strokePaint.color = if (white) 0xFF2A2018.toInt() else 0xFFE8DCC8.toInt()
        strokePaint.strokeWidth = size * 0.045f
        strokePaint.alpha = 255
        fillPaint.alpha = 255

        val s = size
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
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }
}

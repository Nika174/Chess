package com.example.chess.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.example.chess.chess.BISHOP
import com.example.chess.chess.KING
import com.example.chess.chess.KNIGHT
import com.example.chess.chess.PAWN
import com.example.chess.chess.QUEEN
import com.example.chess.chess.ROOK
import com.example.chess.chess.WHITE
import com.example.chess.chess.pieceColor
import com.example.chess.chess.pieceType

/** Shared vector art: soft shadow + vertical gradient, drawn around a translated origin. */
object PieceArt {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x3D000000
    }
    private val path = Path()
    private val shaders = HashMap<Int, Shader>()

    private fun shader(white: Boolean, size: Float): Shader {
        val key = (if (white) 7 else 3) * 100000 + size.toInt()
        return shaders.getOrPut(key) {
            val top = if (white) 0xFFFFFDF7.toInt() else 0xFF544940.toInt()
            val mid = if (white) 0xFFF3E7D4.toInt() else 0xFF2F2821.toInt()
            val bottom = if (white) 0xFFD5C2A6.toInt() else 0xFF100D0A.toInt()
            LinearGradient(
                0f, -size * 0.34f, 0f, size * 0.30f,
                intArrayOf(top, mid, bottom), floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, size: Float, piece: Int) {
        val white = pieceColor(piece) == WHITE
        val s = size

        canvas.save()
        canvas.translate(cx, cy)

        buildPath(s, pieceType(piece))

        canvas.save()
        canvas.translate(0f, s * 0.07f)
        canvas.drawPath(path, shadowPaint)
        canvas.restore()

        fillPaint.shader = shader(white, s)
        canvas.drawPath(path, fillPaint)

        strokePaint.color = if (white) 0xFF6B5844.toInt() else 0xFFB9AC99.toInt()
        strokePaint.strokeWidth = s * 0.03f
        canvas.drawPath(path, strokePaint)

        canvas.restore()
    }

    private fun buildPath(s: Float, type: Int) {
        path.reset()
        when (type) {
            PAWN -> {
                path.addCircle(0f, -s * 0.13f, s * 0.115f, Path.Direction.CW)
                path.addRoundRect(
                    RectF(-s * 0.15f, -s * 0.08f, s * 0.15f, s * 0.20f),
                    s * 0.06f, s * 0.06f, Path.Direction.CW
                )
                path.addRoundRect(
                    RectF(-s * 0.22f, s * 0.18f, s * 0.22f, s * 0.29f),
                    s * 0.05f, s * 0.05f, Path.Direction.CW
                )
            }

            ROOK -> {
                path.addRoundRect(
                    RectF(-s * 0.20f, -s * 0.05f, s * 0.20f, s * 0.22f),
                    s * 0.03f, s * 0.03f, Path.Direction.CW
                )
                path.addRect(-s * 0.22f, -s * 0.24f, s * 0.22f, -s * 0.05f, Path.Direction.CW)
                path.addRect(-s * 0.22f, -s * 0.30f, -s * 0.12f, -s * 0.18f, Path.Direction.CW)
                path.addRect(-s * 0.05f, -s * 0.30f, s * 0.05f, -s * 0.18f, Path.Direction.CW)
                path.addRect(s * 0.12f, -s * 0.30f, s * 0.22f, -s * 0.18f, Path.Direction.CW)
                path.addRoundRect(
                    RectF(-s * 0.25f, s * 0.20f, s * 0.25f, s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }

            KNIGHT -> {
                path.moveTo(-s * 0.16f, s * 0.22f)
                path.lineTo(-s * 0.10f, -s * 0.02f)
                path.lineTo(-s * 0.20f, -s * 0.12f)
                path.lineTo(-s * 0.06f, -s * 0.26f)
                path.lineTo(s * 0.04f, -s * 0.18f)
                path.lineTo(s * 0.18f, -s * 0.06f)
                path.lineTo(s * 0.14f, s * 0.10f)
                path.lineTo(s * 0.10f, s * 0.22f)
                path.close()
                path.addRoundRect(
                    RectF(-s * 0.22f, s * 0.20f, s * 0.22f, s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }

            BISHOP -> {
                path.addCircle(0f, -s * 0.20f, s * 0.07f, Path.Direction.CW)
                path.moveTo(0f, -s * 0.34f)
                path.lineTo(s * 0.17f, s * 0.10f)
                path.lineTo(-s * 0.17f, s * 0.10f)
                path.close()
                path.addRoundRect(
                    RectF(-s * 0.22f, s * 0.18f, s * 0.22f, s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }

            QUEEN -> {
                path.addCircle(-s * 0.18f, -s * 0.24f, s * 0.055f, Path.Direction.CW)
                path.addCircle(0f, -s * 0.31f, s * 0.06f, Path.Direction.CW)
                path.addCircle(s * 0.18f, -s * 0.24f, s * 0.055f, Path.Direction.CW)
                path.moveTo(-s * 0.20f, -s * 0.20f)
                path.lineTo(-s * 0.10f, s * 0.12f)
                path.lineTo(s * 0.10f, s * 0.12f)
                path.lineTo(s * 0.20f, -s * 0.20f)
                path.close()
                path.addRoundRect(
                    RectF(-s * 0.23f, s * 0.18f, s * 0.23f, s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }

            KING -> {
                path.addRect(-s * 0.035f, -s * 0.36f, s * 0.035f, -s * 0.14f, Path.Direction.CW)
                path.addRect(-s * 0.11f, -s * 0.28f, s * 0.11f, -s * 0.21f, Path.Direction.CW)
                path.moveTo(-s * 0.19f, -s * 0.10f)
                path.lineTo(-s * 0.11f, s * 0.12f)
                path.lineTo(s * 0.11f, s * 0.12f)
                path.lineTo(s * 0.19f, -s * 0.10f)
                path.close()
                path.addRoundRect(
                    RectF(-s * 0.23f, s * 0.18f, s * 0.23f, s * 0.30f),
                    s * 0.04f, s * 0.04f, Path.Direction.CW
                )
            }
        }
    }
}

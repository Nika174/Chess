package com.example.chess

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.chess.chess.BISHOP
import com.example.chess.chess.Game
import com.example.chess.chess.KNIGHT
import com.example.chess.chess.QUEEN
import com.example.chess.chess.ROOK
import com.example.chess.chess.WHITE
import com.example.chess.ui.BoardView
import com.example.chess.ui.CapturedView

class MainActivity : AppCompatActivity() {

    private lateinit var board: BoardView
    private lateinit var captured: CapturedView
    private lateinit var tvStatus: TextView
    private lateinit var tvTurn: TextView
    private var game = Game()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        board = findViewById(R.id.board)
        captured = findViewById(R.id.captured)
        tvStatus = findViewById(R.id.tvStatus)
        tvTurn = findViewById(R.id.tvTurn)

        board.game = game
        captured.game = game
        board.onMove = { refreshStatus() }
        board.onPromotion = { moves, apply ->
            val names = arrayOf("Ферзь", "Ладья", "Слон", "Конь")
            val types = intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT)
            AlertDialog.Builder(this)
                .setTitle("Превращение пешки")
                .setItems(names) { _, which ->
                    val chosen = moves.firstOrNull { it.promo == types[which] } ?: moves.first()
                    apply(chosen)
                }
                .setCancelable(false)
                .show()
        }

        findViewById<Button>(R.id.btnNew).setOnClickListener { confirmNewGame() }

        findViewById<Button>(R.id.btnUndo).setOnClickListener {
            if (game.undo()) {
                board.clearSelection()
                refreshStatus()
            } else {
                Toast.makeText(this, "Ходов ещё не было", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnFlip).setOnClickListener {
            board.flipped = !board.flipped
        }

        // long press on the status shows the exact position — handy for reporting bugs
        tvStatus.setOnLongClickListener {
            showFen()
            true
        }

        refreshStatus()
    }

    private fun startNewGame() {
        game = Game()
        board.game = game
        captured.game = game
        refreshStatus()
    }

    private fun confirmNewGame() {
        if (!game.canUndo()) {
            startNewGame()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Начать новую партию?")
            .setMessage("Текущая партия будет потеряна, вернуть ходы не получится.")
            .setPositiveButton("Начать заново") { _, _ -> startNewGame() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showFen() {
        val fen = try {
            game.toFen()
        } catch (t: Throwable) {
            "—"
        }
        AlertDialog.Builder(this)
            .setTitle("Позиция (FEN)")
            .setMessage(fen)
            .setPositiveButton("Копировать") { _, _ ->
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("FEN", fen))
                Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun refreshStatus() {
        val text = try {
            game.statusText()
        } catch (t: Throwable) {
            "Ход ${if (game.side == WHITE) "белых" else "чёрных"}"
        }
        tvStatus.text = text
        tvTurn.text = if (game.side == WHITE) "белые" else "чёрные"
        tvTurn.setTextColor(
            if (game.side == WHITE) 0xFF241A08.toInt() else 0xFFF3ECE2.toInt()
        )
        setChipColor(if (game.side == WHITE) 0xFFE8B44A.toInt() else 0xFF3A3128.toInt())

        captured.game = game
        captured.refresh()

        if (game.gameOver()) {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun setChipColor(color: Int) {
        val bg = GradientDrawable().apply {
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(color)
        }
        tvTurn.background = bg
    }
}

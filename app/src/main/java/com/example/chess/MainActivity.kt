package com.example.chess

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

class MainActivity : AppCompatActivity() {

    private lateinit var board: BoardView
    private lateinit var tvStatus: TextView
    private lateinit var tvTurn: TextView
    private var game = Game()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        board = findViewById(R.id.board)
        tvStatus = findViewById(R.id.tvStatus)
        tvTurn = findViewById(R.id.tvTurn)

        board.game = game
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

        findViewById<Button>(R.id.btnNew).setOnClickListener {
            game = Game()
            board.game = game
            refreshStatus()
        }

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

        refreshStatus()
    }

    private fun refreshStatus() {
        val text = game.statusText()
        tvStatus.text = text
        tvTurn.text = if (game.side == WHITE) "белые" else "чёрные"
        tvTurn.setTextColor(
            if (game.side == WHITE) 0xFFF3ECE2.toInt() else 0xFF2B241D.toInt()
        )
        tvTurn.setBackgroundColor(
            if (game.side == WHITE) 0xFFF0D9B5.toInt() else 0xFF3A3128.toInt()
        )
        if (game.gameOver()) {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }
}

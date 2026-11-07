package com.example.chess

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.chess.chess.BISHOP
import com.example.chess.chess.BLACK
import com.example.chess.chess.Game
import com.example.chess.chess.KNIGHT
import com.example.chess.chess.Move
import com.example.chess.chess.QUEEN
import com.example.chess.chess.ROOK
import com.example.chess.chess.WHITE
import com.example.chess.net.Discovery
import com.example.chess.net.HostInfo
import com.example.chess.net.NetBridge
import com.example.chess.net.NetEvents
import com.example.chess.ui.BoardView
import com.example.chess.ui.CapturedView

class MainActivity : AppCompatActivity() {

    private lateinit var board: BoardView
    private lateinit var captured: CapturedView
    private lateinit var tvStatus: TextView
    private lateinit var tvTurn: TextView
    private lateinit var tvMode: TextView
    private var game = Game()

    private val handler = Handler(Looper.getMainLooper())
    private var net: NetBridge? = null
    private var isHost = false
    private var mySide = -1

    private val port = 8765

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        board = findViewById(R.id.board)
        captured = findViewById(R.id.captured)
        tvStatus = findViewById(R.id.tvStatus)
        tvTurn = findViewById(R.id.tvTurn)
        tvMode = findViewById(R.id.tvMode)

        board.game = game
        captured.game = game

        // guest asks the host to validate every move instead of applying it locally
        board.interceptMove = { m ->
            if (net != null && !isHost) {
                net?.sendMove(m.from, m.to, m.promo)
                true
            } else {
                false
            }
        }
        board.onMove = {
            refreshStatus()
            if (isHost) net?.sendFen(game.toFen())
        }
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
        findViewById<Button>(R.id.btnUndo).setOnClickListener { doUndo() }
        findViewById<Button>(R.id.btnFlip).setOnClickListener { board.flipped = !board.flipped }
        tvTurn.setOnClickListener { showMenu() }

        tvStatus.setOnLongClickListener {
            showFen()
            true
        }

        refreshStatus()
        updateMode("")
    }

    // ---------------- menu & network ----------------

    private fun showMenu() {
        val items = if (net == null) {
            arrayOf("Играть вдвоём на этом телефоне", "Создать игру по Wi-Fi (хост)", "Подключиться к игре")
        } else {
            arrayOf("Выйти из сетевой игры", "Показать мой адрес")
        }
        AlertDialog.Builder(this)
            .setTitle("Режим игры")
            .setItems(items) { _, which ->
                if (net == null) {
                    when (which) {
                        0 -> Unit
                        1 -> startHost()
                        2 -> startGuest()
                    }
                } else {
                    when (which) {
                        0 -> stopNet()
                        1 -> showMyAddress()
                    }
                }
            }
            .show()
    }

    private fun startHost() {
        stopNet()
        isHost = true
        mySide = WHITE
        board.allowedSide = WHITE
        val ip = Discovery.localIp()
        net = NetBridge(handler, events())
        net?.startHost(port, "Шахматы")
        updateMode("Хост • $ip:$port • ждём соперника")
        AlertDialog.Builder(this)
            .setTitle("Ждём соперника")
            .setMessage(
                "Второй телефон: откройте меню (нажмите «белые» сверху) → «Подключиться к игре».\n\n" +
                    "Ваш адрес: $ip:$port\n\nОба телефона должны быть на одной Wi-Fi сети."
            )
            .setPositiveButton("Копировать адрес") { _, _ -> copy("$ip") }
            .setNegativeButton("Понятно", null)
            .show()
    }

    private fun startGuest() {
        stopNet()
        isHost = false
        mySide = -1
        updateMode("Ищу игру в сети…")
        Thread {
            val hosts = try {
                Discovery.findHosts(2500)
            } catch (e: Exception) {
                emptyList<HostInfo>()
            }
            handler.post { chooseHost(hosts) }
        }.also { it.isDaemon = true }.start()
    }

    private fun chooseHost(hosts: List<HostInfo>) {
        if (hosts.isEmpty()) {
            askManualIp()
            return
        }
        val labels = hosts.map { "${it.name} — ${it.ip}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Найденные игры")
            .setItems(labels) { _, which -> connectTo(hosts[which].ip) }
            .setNegativeButton("Ввести адрес вручную") { _, _ -> askManualIp() }
            .show()
    }

    private fun askManualIp() {
        val input = EditText(this)
        input.hint = "192.168.1.10"
        val wrap = FrameLayout(this)
        val pad = (20 * resources.displayMetrics.density).toInt()
        wrap.setPadding(pad, pad / 2, pad, 0)
        wrap.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Адрес хоста")
            .setMessage("Посмотрите адрес на телефоне-хосте (меню → «Создать игру»)")
            .setView(wrap)
            .setPositiveButton("Подключиться") { _, _ ->
                val ip = input.text.toString().trim()
                if (ip.isNotEmpty()) connectTo(ip) else updateMode("")
            }
            .setNegativeButton("Отмена") { _, _ -> updateMode("") }
            .show()
    }

    private fun connectTo(ip: String) {
        stopNet()
        isHost = false
        mySide = -1
        net = NetBridge(handler, events())
        net?.startClient(ip, port)
        updateMode("Подключаюсь к $ip…")
    }

    private fun events() = object : NetEvents {
        override fun onFen(fen: String) {
            if (isHost) return
            if (game.setFromFen(fen)) {
                if (mySide == -1) {
                    mySide = BLACK
                    board.allowedSide = BLACK
                    board.flipped = true
                }
                board.clearSelection()
                refreshStatus()
                updateMode("Сетевая игра • вы чёрные")
            }
        }

        override fun onPeerConnected() {
            if (isHost) {
                board.allowedSide = WHITE
                net?.sendFen(game.toFen())
                updateMode("Сетевая игра • вы белые")
                toast("Соперник подключился")
            } else {
                updateMode("Сетевая игра • подключено")
            }
        }

        override fun onPeerMove(from: Int, to: Int, promo: Int) {
            if (!isHost) return
            val legal = game.allLegalMoves().firstOrNull {
                it.from == from && it.to == to && (promo == 0 || it.promo == promo)
            }
            if (legal != null && game.side != mySide) {
                game.makeMove(legal)
                refreshStatus()
            }
            // always resync: cheapest way to keep both boards identical
            net?.sendFen(game.toFen())
        }

        override fun onPeerNewGame() {
            if (!isHost) startNewGame()
        }

        override fun onClosed(reason: String) {
            toast(reason)
            net?.close()
            net = null
            isHost = false
            mySide = -1
            board.allowedSide = -1
            updateMode("")
            refreshStatus()
        }
    }

    private fun stopNet() {
        net?.close()
        net = null
        isHost = false
        mySide = -1
        board.allowedSide = -1
        updateMode("")
    }

    private fun showMyAddress() {
        val ip = Discovery.localIp()
        AlertDialog.Builder(this)
            .setTitle("Мой адрес")
            .setMessage("$ip:$port")
            .setPositiveButton("Копировать") { _, _ -> copy(ip) }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun copy(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("addr", text))
        toast("Скопировано")
    }

    // ---------------- game actions ----------------

    private fun startNewGame() {
        game = Game()
        board.game = game
        captured.game = game
        refreshStatus()
    }

    private fun confirmNewGame() {
        if (net != null && !isHost) {
            toast("Новую партию начинает хост")
            return
        }
        if (!game.canUndo()) {
            startNewGame()
            if (isHost) net?.sendNewGame()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Начать новую партию?")
            .setMessage("Текущая партия будет потеряна, вернуть ходы не получится.")
            .setPositiveButton("Начать заново") { _, _ ->
                startNewGame()
                if (isHost) net?.sendNewGame()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun doUndo() {
        if (net != null && !isHost) {
            toast("Отменяет хост")
            return
        }
        if (game.undo()) {
            board.clearSelection()
            refreshStatus()
            if (isHost) net?.sendFen(game.toFen())
        } else {
            toast("Ходов ещё не было")
        }
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
            .setPositiveButton("Копировать") { _, _ -> copy(fen) }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    // ---------------- ui ----------------

    private fun updateMode(text: String) {
        if (text.isEmpty()) {
            tvMode.visibility = View.GONE
        } else {
            tvMode.visibility = View.VISIBLE
            tvMode.text = text
        }
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
            toast(text)
        }
    }

    private fun setChipColor(color: Int) {
        val bg = GradientDrawable().apply {
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(color)
        }
        tvTurn.background = bg
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        net?.close()
        super.onDestroy()
    }
}

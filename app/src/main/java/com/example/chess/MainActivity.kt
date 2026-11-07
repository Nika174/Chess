package com.example.chess

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.chess.chess.BISHOP
import com.example.chess.chess.BLACK
import com.example.chess.chess.Game
import com.example.chess.chess.KNIGHT
import com.example.chess.chess.Move
import com.example.chess.chess.QUEEN
import com.example.chess.chess.ROOK
import com.example.chess.chess.WHITE
import com.example.chess.media.MediaLink
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
    private lateinit var btnNet: Button
    private lateinit var btnVideo: Button
    private var game = Game()

    private val handler = Handler(Looper.getMainLooper())
    private var net: NetBridge? = null
    private var isHost = false
    private var mySide = -1

    private var media: MediaLink? = null
    private var peerView: ImageView? = null
    private var peerIp: String? = null
    private var synced = 0

    private val port = 8765

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        board = findViewById(R.id.board)
        captured = findViewById(R.id.captured)
        tvStatus = findViewById(R.id.tvStatus)
        tvTurn = findViewById(R.id.tvTurn)
        tvMode = findViewById(R.id.tvMode)
        btnNet = findViewById(R.id.btnNet)
        btnVideo = findViewById(R.id.btnVideo)

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
        board.onMove = { refreshStatus() }
        board.onMovePlayed = { m ->
            if (isHost) {
                // belt and braces: send the move itself and the whole position
                net?.sendMove(m.from, m.to, m.promo)
                net?.sendFen(game.toFen())
                synced++
                showSync()
            }
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
        btnNet.setOnClickListener { showNetMenu() }
        btnVideo.setOnClickListener { if (media == null) toggleVideo() else stopVideo() }
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
            arrayOf(
                if (media == null) "Включить видео и звук" else "Выключить видео и звук",
                "Выйти из сетевой игры",
                "Показать мой адрес"
            )
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
                        0 -> if (media == null) toggleVideo() else stopVideo()
                        1 -> stopNet()
                        2 -> showMyAddress()
                    }
                }
            }
            .show()
    }

    private fun showNetMenu() {
        if (net != null) {
            AlertDialog.Builder(this)
                .setTitle("Сетевая игра")
                .setItems(arrayOf("Выйти из сетевой игры", "Показать мой адрес")) { _, which ->
                    if (which == 0) stopNet() else showMyAddress()
                }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Игра по сети (Wi-Fi)")
            .setItems(
                arrayOf(
                    "Создать игру — я хост (белые)",
                    "Подключиться к игре (чёрные)",
                    "Проверить связь на этом телефоне"
                )
            ) { _, which ->
                when (which) {
                    0 -> startHost()
                    1 -> startGuest()
                    else -> selfTest()
                }
            }
            .show()
    }

    /** loopback test: host + client on the same phone, proves the protocol works */
    private fun selfTest() {
        val testPort = 8799
        var hostAccepted = false
        var clientSawHost = false
        var fenDelivered = false
        var hostBridge: NetBridge? = null

        val host = NetBridge(handler, object : NetEvents {
            override fun onFen(fen: String) {}
            override fun onPeerConnected() {
                hostAccepted = true
            }
            override fun onPeerMove(from: Int, to: Int, promo: Int) {}
            override fun onPeerNewGame() {}
            override fun onClosed(reason: String) {}
        })
        hostBridge = host

        val client = NetBridge(handler, object : NetEvents {
            override fun onFen(fen: String) {
                if (fen == "TEST-FEN") fenDelivered = true
            }
            override fun onPeerConnected() {
                clientSawHost = true
            }
            override fun onPeerMove(from: Int, to: Int, promo: Int) {}
            override fun onPeerNewGame() {}
            override fun onClosed(reason: String) {}
        })

        host.startHost(testPort, "test")
        handler.postDelayed({
            hostBridge?.sendFen("TEST-FEN")
            hostBridge?.sendMove(1, 2, 0)
            client.startClient("127.0.0.1", testPort)
        }, 500)
        handler.postDelayed({
            host.close()
            client.close()
            val ok = hostAccepted && clientSawHost && fenDelivered
            AlertDialog.Builder(this)
                .setTitle(if (ok) "Связь работает" else "Связь не работает")
                .setMessage(
                    "хост принял: $hostAccepted
" +
                        "гость увидел хост: $clientSawHost
" +
                        "позиция дошла: $fenDelivered"
                )
                .setPositiveButton("Ок", null)
                .show()
        }, 2500)
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
        peerIp = ip
        isHost = false
        mySide = -1
        net = NetBridge(handler, events())
        net?.startClient(ip, port)
        updateMode("Подключаюсь к $ip…")
    }

    private fun events() = object : NetEvents {
        override fun onFen(fen: String) {
            if (isHost) return
            val ok = try { game.setFromFen(fen) } catch (t: Throwable) { false }
            if (ok) {
                synced++
                if (mySide == -1) {
                    mySide = BLACK
                    board.allowedSide = BLACK
                    board.flipped = true
                }
                board.clearSelection()
                refreshStatus()
                showSync()
            } else {
                toast("не удалось прочитать позицию: $fen")
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
            if (!isHost) {
                val legal = game.allLegalMoves().firstOrNull {
                    it.from == from && it.to == to && (promo == 0 || it.promo == promo)
                }
                if (legal != null && game.side != mySide) {
                    game.makeMove(legal)
                    board.clearSelection()
                    refreshStatus()
                }
                synced++
                showSync()
                return
            }
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
        stopVideo()
        net?.close()
        net = null
        isHost = false
        mySide = -1
        board.allowedSide = -1
        updateMode("")
    }

    private fun showSync() {
        val side = when {
            mySide == WHITE -> "вы белые"
            mySide == BLACK -> "вы чёрные"
            else -> "сеть"
        }
        updateMode("Сетевая игра • $side • обновлений: $synced")
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

    // ---------------- video call ----------------

    private fun toggleVideo() {
        val missing = ArrayList<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 7)
            return
        }
        startVideo()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startVideo()
            } else {
                toast("Без камеры и микрофона видео не работает")
            }
        }
    }

    private fun startVideo() {
        if (net == null) {
            toast("Сначала подключитесь к игре по Wi-Fi")
            return
        }
        showOverlay(true)
        val link = MediaLink(
            handler,
            { bmp: Bitmap -> peerView?.setImageBitmap(bmp) },
            { reason: String ->
                toast(reason)
                stopVideo()
            }
        )
        media = link
        if (isHost) link.startHost() else link.startGuest(peerIp ?: return)
        updateMode("Видеосвязь включена")
    }

    private fun stopVideo() {
        media?.stop()
        media = null
        peerView?.visibility = View.GONE
        if (net != null) updateMode("Сетевая игра")
    }

    private fun showOverlay(show: Boolean) {
        if (!show) {
            peerView?.visibility = View.GONE
            return
        }
        var iv = peerView
        if (iv == null) {
            iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.background = GradientDrawable().apply {
                cornerRadius = 16f * resources.displayMetrics.density
                setColor(0xFF161B22.toInt())
            }
            iv.clipToOutline = true
            val density = resources.displayMetrics.density
            val w = (120 * density).toInt()
            val h = (90 * density).toInt()
            val lp = FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.END)
            lp.topMargin = (10 * density).toInt()
            lp.rightMargin = (10 * density).toInt()
            addContentView(iv, lp)
            peerView = iv
        }
        iv.visibility = View.VISIBLE
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
        btnNet.text = if (net != null) "Выйти из сети" else "Игра по сети"
        btnVideo.text = if (media != null) "Видео включено" else "Видео и звук"
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
        stopVideo()
        net?.close()
        super.onDestroy()
    }
}

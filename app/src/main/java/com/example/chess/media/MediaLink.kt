package com.example.chess.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Peer-to-peer video + voice for two phones on the same Wi-Fi.
 * Frames: [len:int][type:byte][payload]. type 1 = JPEG, type 2 = 16 kHz mono PCM.
 * No third-party libraries, no internet, no accounts.
 */
class MediaLink(
    private val handler: Handler,
    private val onPeerFrame: (Bitmap) -> Unit,
    private val onClosed: (String) -> Unit,
    private val log: (String) -> Unit = {}
) {

    companion object {
        private const val TAG = "MediaLink"
        private const val TYPE_VIDEO = 1
        private const val TYPE_AUDIO = 2
        private const val SAMPLE_RATE = 16000
        private const val FRAME_INTERVAL_MS = 100L   // ~10 fps
        const val PORT = 8767
    }

    @Volatile private var running = false
    @Volatile private var enabled = false
    private var capturing = false
    @Volatile private var socket: Socket? = null
    @Volatile private var out: DataOutputStream? = null
    private var server: ServerSocket? = null
    private var camera: Camera? = null
    private var camThread: HandlerThread? = null
    private var frameSeen = false
    private var audioRecord: AudioRecord? = null
    private var track: AudioTrack? = null

    // ---------------- setup ----------------

    fun startHost() {
        stop()
        running = true
        Thread {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(PORT))
                server = ss
                val s = ss.accept()
                attach(s)
            } catch (e: Exception) {
                if (running) fail("видео-сервер: ${e.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    fun startGuest(host: String) {
        stop()
        running = true
        Thread {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, PORT), 5000)
                attach(s)
            } catch (e: Exception) {
                if (running) fail("видео: не удалось подключиться")
            }
        }.also { it.isDaemon = true }.start()
    }

    fun isRunning() = running

    private fun fail(message: String) {
        if (!running) return
        running = false
        handler.post { onClosed(message) }
    }

    /** start sending our camera and mic; safe to call before the peer connects */
    fun enable() {
        enabled = true
        if (socket != null) {
            Thread { startCapture() }.also { it.isDaemon = true }.start()
        }
    }

    private fun startCapture() {
        if (capturing) return
        capturing = true
        startCamera()
        startMic()
        startPlayback()
    }

    private fun attach(s: Socket) {
        s.tcpNoDelay = true
        socket = s
        out = DataOutputStream(s.getOutputStream())
        startReceiver(s)
        if (enabled) Thread { startCapture() }.also { it.isDaemon = true }.start()
    }

    // ---------------- receive ----------------

    private fun startReceiver(s: Socket) {
        Thread {
            try {
                val input = DataInputStream(s.getInputStream())
                while (running) {
                    val len = input.readInt()
                    if (len <= 0 || len > 2_000_000) break
                    val type = input.readByte().toInt()
                    val data = ByteArray(len)
                    input.readFully(data)
                    when (type) {
                        TYPE_VIDEO -> {
                            val bmp = BitmapFactory.decodeByteArray(data, 0, len)
                            if (bmp != null) {
                                if (!frameSeen) {
                                    frameSeen = true
                                    log("получен первый кадр")
                                }
                                handler.post { onPeerFrame(bmp) }
                            } else {
                                log("кадр не декодировался")
                            }
                        }
                        TYPE_AUDIO -> {
                            try {
                                track?.write(data, 0, len)
                            } catch (e: Exception) {
                                // playback hiccup
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // peer gone
            } finally {
                fail("видеосвязь закрыта")
            }
        }.also { it.isDaemon = true }.start()
    }

    // ---------------- send ----------------

    private fun send(type: Int, payload: ByteArray) {
        val o = out ?: return
        try {
            synchronized(this) {
                o.writeInt(payload.size)
                o.writeByte(type)
                o.write(payload)
                o.flush()
            }
        } catch (e: Exception) {
            fail("видеосвязь прервана")
        }
    }

    // ---------------- camera ----------------

    private fun startCamera() {
        val thread = HandlerThread("chess-cam")
        thread.start()
        camThread = thread
        Handler(thread.looper).post {
            try {
                val info = Camera.CameraInfo()
                var index = -1
                for (i in 0 until Camera.getNumberOfCameras()) {
                    Camera.getCameraInfo(i, info)
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        index = i
                        break
                    }
                }
                val cam = if (index >= 0) Camera.open(index) else Camera.open()
                camera = cam

                val params = cam.parameters
                val supported = params.supportedPreviewSizes
                val wanted = supported
                    .filter { it.width <= 480 && it.height <= 360 }
                    .maxByOrNull { it.width * it.height }
                    ?: supported.minByOrNull { it.width * it.height }
                if (wanted != null) params.setPreviewSize(wanted.width, wanted.height)
                params.previewFormat = ImageFormat.NV21
                cam.parameters = params

                try {
                    cam.setPreviewTexture(android.graphics.SurfaceTexture(0))
                } catch (e: Exception) {
                    log("камера: нет текстуры предпросмотра (${e.message})")
                }
                cam.startPreview()
                log("камера запущена")

                var lastSent = 0L
                val buffer = ByteArrayOutputStream(64 * 1024)
                cam.setPreviewCallback { data, _ ->
                    val now = System.currentTimeMillis()
                    if (!running || now - lastSent < FRAME_INTERVAL_MS) return@setPreviewCallback
                    lastSent = now
                    try {
                        val p = cam.parameters
                        val w = p.previewSize.width
                        val h = p.previewSize.height
                        buffer.reset()
                        val yuv = YuvImage(data, ImageFormat.NV21, w, h, null)
                        yuv.compressToJpeg(Rect(0, 0, w, h), 45, buffer)
                        send(TYPE_VIDEO, buffer.toByteArray())
                    } catch (e: Exception) {
                        log("кадр: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                log("камера не открылась: ${e.message}")
            }
        }
    }

    // ---------------- mic ----------------

    private fun startMic() {
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE / 10 * 2)
            )
            audioRecord = rec
            rec.startRecording()
            Thread {
                val buf = ByteArray(SAMPLE_RATE / 50 * 2) // 20 ms
                while (running) {
                    val read = rec.read(buf, 0, buf.size)
                    if (read > 0) send(TYPE_AUDIO, buf.copyOf(read))
                }
            }.also { it.isDaemon = true }.start()
        } catch (e: Exception) {
            Log.w(TAG, "mic: ${e.message}")
        }
    }

    // ---------------- speaker ----------------

    private fun startPlayback() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val t = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE / 5 * 2),
                AudioTrack.MODE_STREAM
            )
            track = t
            t.play()
        } catch (e: Exception) {
            Log.w(TAG, "playback: ${e.message}")
        }
    }

    // ---------------- teardown ----------------

    fun stop() {
        running = false
        enabled = false
        capturing = false
        try { camera?.setPreviewCallback(null) } catch (e: Exception) {}
        try { camera?.stopPreview() } catch (e: Exception) {}
        try { camera?.release() } catch (e: Exception) {}
        camera = null
        try { camThread?.quitSafely() } catch (e: Exception) {}
        camThread = null
        frameSeen = false
        try { audioRecord?.stop() } catch (e: Exception) {}
        try { audioRecord?.release() } catch (e: Exception) {}
        audioRecord = null
        try { track?.stop() } catch (e: Exception) {}
        try { track?.release() } catch (e: Exception) {}
        track = null
        try { socket?.close() } catch (e: Exception) {}
        try { server?.close() } catch (e: Exception) {}
        socket = null
        server = null
        out = null
    }
}

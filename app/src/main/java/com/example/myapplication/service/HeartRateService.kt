package com.example.myapplication.service

import android.os.Build
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import android.content.Context
import kotlin.time.Duration.Companion.milliseconds
import androidx.wear.ongoing.OngoingActivity
import android.app.PendingIntent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.hardware.SensorEventListener
import com.example.myapplication.presentation.MainActivity
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.myapplication.R

const val DEBUG_MODE = true // Mudar para false em prod

// Defining constant values
const val SOCKET_CONNECTING = 0
const val SOCKET_OPEN = 1

const val SOCKET_CLOSING = 2
const val SOCKET_CLOSED = 3

const val MAX_ATTEMPTS = 5 // Máximo de tentativas de reconexão
const val BATCH_SIZE = 10 // Tamanho dos lotes de dados a serem enviados via o WebSocket

class HeartRateService : Service() {
    private val client = OkHttpClient.Builder()
        .build()
    private var ws: WebSocket? = null

    private val TAG = "HeartRateSystem"

    // This should be your desktop's IPV4 on the local network, on the desired port.
    private val URL = "ws://192.168.15.50:8000/ws/watch"


    // Variables used for the recovery of the connection.
    private var internalTimer: Int = 0
    private var attempts: Int = 0

    private var SOCKET_STATUS: Int = SOCKET_CLOSED

    //private var wakeLock: PowerManager.WakeLock? = null
    //private var wifiLock: WifiManager.WifiLock? = null
    private val dataBatch = mutableListOf<String>()


    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // Variáveis do Bare-Metal Sensor
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Begun system")

        // SDK_INT >= 34 requires us to declare the foregroundServiceType.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(1, createNotification())
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Wi-Fi available, using it to send data...")
                connectivityManager.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Wi-Fi connection lost, cancelling dead socket")
                connectivityManager.bindProcessToNetwork(null)
                ws?.cancel()
            }
        }

        connectivityManager.requestNetwork(networkRequest, networkCallback)

        connectServer()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        Log.d(TAG, "System destroyed")
        stopSensorClient()
        disconnectServer()
    }

    private fun connectServer() {
        if (ws != null && (SOCKET_STATUS == SOCKET_CONNECTING || SOCKET_STATUS == SOCKET_OPEN)) {
            Log.d(TAG, "Watch WebSocket already exists AND is connecting / connected")
            return
        }

        SOCKET_STATUS = SOCKET_CONNECTING
        val request = Request.Builder().url(URL).build()

        val listener = object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                SOCKET_STATUS = SOCKET_OPEN
                Log.d(TAG, "Websocket opened successfully")
                showToast("Conexão estabelecida")
                startSensorClient()

            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                SOCKET_STATUS = SOCKET_CLOSING
                Log.d(TAG, "Websocket is closing")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                SOCKET_STATUS = SOCKET_CLOSED
                Log.d(TAG, "Websocket closed with code $code and reason $reason")
                stopSensorClient()
                dataBatch.clear()
                if(code != 1000) {
                    ws = null
                    reattemptConnection()
                }
                else
                {
                    showToast("Desconectado do servidor")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                SOCKET_STATUS = SOCKET_CLOSED
                Log.d(TAG, "Websocket failed to connect: ${t.message}")
                stopSensorClient()
                dataBatch.clear()
                ws = null
                reattemptConnection()
            }
        }

        ws = client.newWebSocket(request, listener)

    }

    private fun disconnectServer() {
        if (ws == null || SOCKET_STATUS == SOCKET_CLOSING || SOCKET_STATUS == SOCKET_CLOSED)
        {
            Log.w(TAG, "Watch WebSocket doesn't exist OR is closing / closed")
            return
        }
        ws?.close(1000, "Graceful disconnect")
        ws = null
    }

    private fun reattemptConnection() {
        attempts++
        if (attempts > MAX_ATTEMPTS) {
            Log.d(TAG, "Amount of attempts $attempts surpassed maximum of $MAX_ATTEMPTS")
            showToast("Não foi possível conectar ao servidor")
            return
        }
        Log.d(TAG, "lost connection! reattempting connection... (on attempt ${attempts})")

        CoroutineScope(Dispatchers.IO).launch {
            delay((if (attempts >= 4) 1000*(1 shl 5) else 1000*(1 shl attempts)).milliseconds)
            connectServer()
            if(SOCKET_STATUS == SOCKET_OPEN)
            {
                Log.d(TAG, "Managed to reconnect within $attempts attempts")
            }
        }
    }

    // --- BARE METAL SENSOR IMPLEMENTATION ---
    private fun startSensorClient()
    {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if(heartRateSensor == null)
        {
            Log.e(TAG, "ERRO CRÍTICO: Nenhum sensor de hardware PPG encontrado!")
        }
        else
        {
            // Registra o ouvinte com taxa de atualização normal (ideal para economia de bateria e envio via batch)
            val isRegistered = sensorManager.registerListener(
                sensorListener,
                heartRateSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d(TAG, "Sensor listener registrado no HAL com sucesso $isRegistered")
        }
    }

    private fun stopSensorClient()
    {
        if(::sensorManager.isInitialized)
        {
            sensorManager.unregisterListener(sensorListener)
            Log.d(TAG, "Unregistered sensorListener")
        }
        else
        {
            Log.w(TAG, "No registering done: sensorManager was not initialized. Verify that there's an active WebSocket connection.")
        }
    }


    private val sensorListener = object : SensorEventListener
    {
        override fun onSensorChanged(event: SensorEvent?)
        {
            if(event?.sensor?.type == Sensor.TYPE_HEART_RATE)
            {
                // O SensorManager retorna um float, precisamos converter
                val bpm = event.values[0].toInt()
                val accuracy = event.accuracy

                if(accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT)
                {
                    Log.d(TAG, "Sensor status is unreliable or there's no skin contact!")
                    return
                }


                if(bpm > 0)
                {
                    // LOG DE DIAGNÓSTICO: Isso vai inundar o console se o hardware estiver livre
                    Log.d(TAG, "HAL Sensor Update: $bpm BPM (Timer: $internalTimer)")
                    val jsonPayload = """{"TIME":${internalTimer}, "HEART_RATE":${bpm}}"""
                    dataBatch.add(jsonPayload)
                    internalTimer++

                    if(dataBatch.size >= BATCH_SIZE)
                    {
                        if(DEBUG_MODE)
                        {
                            val vibrator = getSystemService(Vibrator::class.java)
                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                        }

                        Log.d(TAG, "Sending payload with ${dataBatch.size} elements")
                        dataBatch.forEach { ws?.send(it) }
                        dataBatch.clear()
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // LOG DE BLOQUEIO: Se o Samsung Health roubar o sensor, a precisão frequentemente cai para 0 (UNRELIABLE)
            val accuracyString = when(accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE (Possível bloqueio ou sem contato)"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
                else -> "UNKNOWN ($accuracy)"
            }
            Log.w(TAG, "Sensor HAL Accuracy Changed: $accuracyString")
        }
    }



    // -----------------------------------------

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "heart_rate_channel"
        val channel = NotificationChannel(channelId, "Monitoring", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Heart Monitoring")
            .setContentText("Tracking & sending data via websockets...")
            .setSmallIcon(R.drawable.ic_heart_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext,
            1,
            builder
        )
            .setStaticIcon(R.drawable.ic_heart_notification)
            .setTouchIntent(pendingIntent)
            .build()

        ongoingActivity.apply(applicationContext)

        return builder.build()
    }
}
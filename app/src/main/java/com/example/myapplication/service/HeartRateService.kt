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
import kotlin.random.Random

const val DEBUG_MODE = true // Change to false in prod

// Defining constant values
const val SOCKET_CONNECTING = 0
const val SOCKET_OPEN = 1

const val SOCKET_CLOSING = 2
const val SOCKET_CLOSED = 3

const val MAX_ATTEMPTS = 5 //
const val BATCH_SIZE = 10 // Amount of data points to be sent via the WebSocket

// This should be your desktop's IPV4 on the local network, on the desired port.
const val URL = "ws://192.168.15.50:8000/ws/watch"

class HeartRateService : Service() {
    private val client = OkHttpClient.Builder().build()
    private var ws: WebSocket? = null
    private val TAG = "HeartRateSystem"


    // Variables used for the recovery of the connection.
    private var internalTimer: Int = 0
    private var attempts: Int = 0

    private var SOCKET_STATUS: Int = SOCKET_CLOSED // Socket begins as closed
    private val dataBatch = mutableListOf<String>() // Our data will be stored in this batch

    // Wi-Fi connectivity-related variables.
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    // Sensor-related variables.
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null

    /* The app should be running in the background until the user explicitly stops.
    In that case, we don't want our app to be bound to any other service / component,
    so we need to do the following function override.
    */
    override fun onBind(intent: Intent?): IBinder?
    {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        Log.d(TAG, "Begun system")

        // SDK_INT >= 34 requires us to declare the foregroundServiceType.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        }
        else
        {
            startForeground(1, createNotification())
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // We'll send our data via Wi-Fi, since we assume that the watch, the server and the client are on the same local network.
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback()
        {
            override fun onAvailable(network: Network)
            {
                Log.d(TAG, "Wi-Fi available for data sending")
                connectivityManager.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network)
            {
                Log.w(TAG, "Wi-Fi connection lost, cancelling dead socket")
                connectivityManager.bindProcessToNetwork(null)
                ws?.cancel()
            }
        }

        connectivityManager.requestNetwork(networkRequest, networkCallback)
        connectServer()

        return START_STICKY // Promises that this service will be recreated if killed by kernel (whatever the case may be)
    }

    override fun onDestroy()
    {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        Log.d(TAG, "System destroyed")
        stopSensorClient()
        disconnectServer()
    }

    private fun connectServer()
    {
        if(ws != null && (SOCKET_STATUS == SOCKET_CONNECTING || SOCKET_STATUS == SOCKET_OPEN))
        {
            Log.d(TAG, "Watch WebSocket already exists AND is connecting / connected")
            return
        }
        SOCKET_STATUS = SOCKET_CONNECTING
        val request = Request.Builder().url(URL).build()

        val listener = object : WebSocketListener()
        {
            override fun onOpen(webSocket: WebSocket, response: Response)
            {
                SOCKET_STATUS = SOCKET_OPEN
                Log.d(TAG, "WebSocket opened successfully")
                showToast("Conexão estabelecida")
                startSensorClient()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String)
            {
                SOCKET_STATUS = SOCKET_CLOSING
                Log.d(TAG, "WebSocket is closing")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String)
            {
                SOCKET_STATUS = SOCKET_CLOSED
                Log.d(TAG, "WebSocket closed with code $code and reason $reason")
                stopSensorClient()
                dataBatch.clear()
                if(code != 1000) {
                    ws = null
                    reattemptConnection()
                }
                else
                {
                    showToast("Conexão terminada")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?)
            {
                SOCKET_STATUS = SOCKET_CLOSED
                Log.d(TAG, "WebSocket failed to connect: ${t.message}")
                stopSensorClient()
                dataBatch.clear()
                ws = null
                reattemptConnection()
            }
        }

        ws = client.newWebSocket(request, listener)
    }

    private fun disconnectServer()
    {
        if(ws == null || SOCKET_STATUS == SOCKET_CLOSING || SOCKET_STATUS == SOCKET_CLOSED)
        {
            Log.w(TAG, "Watch WebSocket doesn't exist OR is closing / closed")
            return
        }
        ws?.close(1000, "Graceful disconnect")
        ws = null
    }

    private fun reattemptConnection()
    {
        attempts++
        if(attempts > MAX_ATTEMPTS)
        {
            Log.w(TAG, "Amount of reconnection attempts $attempts surpassed maximum of $MAX_ATTEMPTS, quitting...")
            showToast("Não foi possível conectar ao servidor após $attempts tentativas")
            return
        }
        Log.d(TAG, "lost connection! reattempting connection... (attempt $attempts)")

        //
        CoroutineScope(Dispatchers.IO).launch {
            // Attempts of 2, 4, 8, 16, 32 seconds, with a bit of jitter.
            delay((1000 * (1 shl attempts) + (Random.nextDouble(0.0, 1.0) * 2000).toInt()).milliseconds)
            connectServer()
            if(SOCKET_STATUS == SOCKET_OPEN)
            {
                Log.d(TAG, "Managed to reconnect within $attempts attempts")
            }
        }
    }

    // From here, we handle the sensor logic. TODO: Check if we can modularize this section.
    // Since SDK_INT < 36, We only need BODY_SENSORS permission. Otherwise, we'll need READ_HEART_RATE permission.
    private fun startSensorClient()
    {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if(heartRateSensor == null)
        {
            Log.e(TAG, "ERROR: No sensor found on device")
        }
        else
        {
            val isRegistered = sensorManager.registerListener(
                sensorListener,
                heartRateSensor,
                SensorManager.SENSOR_DELAY_NORMAL // Obtain data at a relatively ok frequency
            )
            Log.d(TAG, "Successfully registered the Sensor $isRegistered")
        }
    }

    private fun stopSensorClient()
    {
        if(::sensorManager.isInitialized)
        {
            sensorManager.unregisterListener(sensorListener)
            Log.d(TAG, "Unregistered active sensor")
        }
        else
        {
            Log.w(TAG, "No registering done: sensorManager was not initialized. Verify that there's an active WebSocket connection.")
        }
    }

    // As the sensor obtains data, we check for its validity (sensor reliability status) and store the obtained data in batches before sending them.
    private val sensorListener = object : SensorEventListener
    {
        override fun onSensorChanged(event: SensorEvent?)
        {
            val bpm = event?.values[0]?.toInt()
            val accuracy = event?.accuracy

            if(accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT)
            {
                Log.w(TAG, "Sensor status is unreliable or there's no skin contact!")
                return
            }

            if(bpm != null && bpm > 0)
            {
                Log.d(TAG, "Sensor Update: $bpm BPM (Timer: $internalTimer)")
                val jsonPayload = """{"TIME":${internalTimer}, "HEART_RATE":${bpm}}"""
                dataBatch.add(jsonPayload)
                internalTimer++

                if(dataBatch.size >= BATCH_SIZE)
                {
                    if(DEBUG_MODE) // TODO: Configure debug mode better
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


        // Displays sensor accuracy. Purely for debug purposes here.
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int)
        {
            val accuracyString = when(accuracy)
            {
                SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE (Blocked or no contact with skin)"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
                else -> "UNKNOWN ($accuracy)"
            }
            Log.w(TAG, "Sensor accuracy changed: $accuracyString")
        }
    }



    // From this point onwards, everything is related to sending notifications.
    // TODO: Modularize. Maybe.

    private fun showToast(message: String)
    {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotification(): Notification
    {
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
package com.gogumac.echovoice


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

class EchoService : Service() {

    private val TAG = "EchoService"
    private val CHANNEL_ID = "EchoServiceChannel"
    private val NOTIFICATION_ID = 1

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false // To manage playback state

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = 0

    private val serviceJob = Job()
    private val serviceScope =
        CoroutineScope(Dispatchers.IO + serviceJob)

    private var echoDelayMillis = 500L // 1 seconds delay

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)* 20
        Log.d(TAG, "onCreate: MinRecordBufferSize reported: $bufferSize")

        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size")
            stopSelf() // Stop service if buffer size is invalid
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Prioritize voice, less likely to pick up media
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA) // For voice playback
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for AudioRecord or AudioTrack: ${e.message}")
            stopSelf()
            return
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgumentException for AudioRecord or AudioTrack: ${e.message}")
            stopSelf()
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "EchoService started")
        startEcho()
        return START_STICKY // Service will be restarted if killed by the system
    }

    private fun startEcho() {
        Log.d(TAG, "startEcho: Attempting to start echo...")
        if (isRecording || audioRecord == null || audioTrack == null) {
            Log.d(TAG, "Already recording or audio components not initialized.")
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "startEcho: AudioRecord not initialized. State: ${audioRecord?.state}")
            return
        }
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "startEcho: AudioTrack not initialized. State: ${audioTrack?.state}")
            return
        }

        if (bufferSize <= 0) {
            Log.e(TAG, "startEcho: Invalid bufferSize: $bufferSize. Cannot start.")
            return
        }


        try {
            Log.d(TAG, "startEcho: Starting AudioRecord recording...")
            audioRecord?.startRecording()
            Log.d(TAG, "startEcho: AudioRecord recordingState: ${audioRecord?.recordingState}")
            Log.d(TAG, "startEcho: Starting AudioTrack playback...")
            audioTrack?.play() // Start playback to be ready
            Log.d(TAG, "startEcho: AudioTrack playState: ${audioTrack?.playState}")
            isRecording = true
            isPlaying = true // Assume playback will start
            Log.d(TAG, "startEcho: isRecording=true, isPlaying=true")

            serviceScope.launch {
                val audioBuffer = ByteArray(bufferSize)
                while (isRecording && isActive) {
                    val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                    if (readResult > 0) {
                        Log.i(TAG, "Loop: Read $readResult bytes from AudioRecord. Delaying for $echoDelayMillis ms...")
                        echoDelayMillis = getSharedPreferences("echo_prefs", MODE_PRIVATE).getInt("delay", 500).toLong()
                        // Introduce delay before playing
                        delay(echoDelayMillis)
                        if (isPlaying && isActive) { // Check if still supposed to be playing
                            Log.i(TAG, "Loop: Attempting to write $readResult bytes to AudioTrack.")
                            val writeResult = audioTrack?.write(audioBuffer, 0, readResult)
                            Log.i(TAG, "Loop: AudioTrack writeResult: $writeResult")
                            if (writeResult!= null && writeResult < 0) {
                                Log.e(TAG, "Loop: Error writing to AudioTrack: $writeResult")
                            }
                        } else {
                        Log.d(TAG, "Loop: Skipped playback: isPlaying=$isPlaying, isActive=$isActive")
                    }
                    }else if (readResult < 0) {
                        Log.e(TAG, "Loop: Error reading from AudioRecord: $readResult")
                        // Consider breaking or handling specific errors like ERROR_INVALID_OPERATION
                    }
                }
                Log.d(TAG, "startEcho: Echo loop finished. isRecording: $isRecording, isActive: $isActive")
                // Clean up states after loop finishes
                isRecording = false
                isPlaying = false
                audioRecord?.stop()
                audioTrack?.stop()
                Log.d(TAG, "startEcho: AudioRecord and AudioTrack stopped after loop.")
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException during startEcho: ${e.message}")
            stopEcho() // Clean up if something goes wrong
        }
    }

    private fun stopEcho() {
        isRecording = false
        isPlaying = false
        serviceScope.coroutineContext.cancelChildren() // Cancel ongoing coroutines

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException during stopEcho: ${e.message}")
        }
        Log.d(TAG, "EchoService stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEcho()
        serviceJob.cancel()
        Log.d(TAG, "EchoService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Echo Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(
            this,
            MainActivity::class.java
        ) // Replace MainActivity with your app's main activity
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Echo Service")
            .setContentText("Echoing your voice...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Replace with your notification icon
            .setContentIntent(pendingIntent)
            .build()
    }
}

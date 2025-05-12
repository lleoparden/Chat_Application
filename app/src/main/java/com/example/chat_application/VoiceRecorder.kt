package com.example.chat_application

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var fileName: String = ""
    private var startTime: Long = 0

    fun startRecording(): String {
        fileName = createFileName()

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(fileName)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

                try {
                    prepare()
                    start()
                    startTime = System.currentTimeMillis()
                    Log.d("VoiceRecorder", "Recording started at $fileName")
                } catch (e: IOException) {
                    Log.e("VoiceRecorder", "Recording failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error initializing recorder: ${e.message}")
        }

        return fileName
    }

    fun stopRecording(): Pair<String, Int> {
        val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()

        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            Log.d("VoiceRecorder", "Recording stopped, duration: $duration seconds")
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error stopping recording: ${e.message}")
        }

        return Pair(fileName, duration)
    }

    private fun createFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.filesDir, "VoiceNotes")

        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        return "${storageDir.absolutePath}/VN_$timeStamp.3gp"
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (e: IllegalStateException) {
                    // Recorder might not be in recording state
                    Log.e("VoiceRecorder", "Recorder was not in recording state: ${e.message}")
                } finally {
                    release()
                }
            }
            recorder = null

            // Delete the file if it exists
            if (fileName.isNotEmpty()) {
                val file = File(fileName)
                if (file.exists()) {
                    file.delete()
                    Log.d("VoiceRecorder", "Recording canceled and file deleted")
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error canceling recording: ${e.message}")
        }
    }

    fun encodeFileToBase64(filePath: String): String {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e("VoiceRecorder", "File does not exist for Base64 encoding: $filePath")
                return ""
            }

            // Read the file into a byte array
            val fileInputStream = FileInputStream(file)
            val byteArrayOutputStream = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var bytesRead: Int

            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead)
            }

            // Convert to Base64
            val audioBytes = byteArrayOutputStream.toByteArray()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.DEFAULT)

            // Clean up
            fileInputStream.close()
            byteArrayOutputStream.close()

            Log.d("VoiceRecorder", "File encoded to Base64, size: ${base64Audio.length} chars")
            return base64Audio

        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error encoding file to Base64: ${e.message}")
            return ""
        }
    }

    fun saveBase64ToFile(base64String: String, outputFilePath: String): Boolean {
        try {
            val audioBytes = Base64.decode(base64String, Base64.DEFAULT)
            val outputFile = File(outputFilePath)

            // Create directory if it doesn't exist
            outputFile.parentFile?.mkdirs()

            // Write bytes to file
            outputFile.writeBytes(audioBytes)
            Log.d("VoiceRecorder", "Base64 data saved to file: $outputFilePath")
            return true

        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Error saving Base64 to file: ${e.message}")
            return false
        }
    }
}
package com.example.chat_application

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import java.io.File

class VoiceNotePlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var seekBar: SeekBar? = null
    private var durationText: TextView? = null
    private var playPauseButton: ImageButton? = null

    // CRITICAL FIX: We need to handle clicks on the button separately from the actual player state
    private var buttonClickListener: (() -> Unit)? = null

    fun playVoiceNote(
        filePath: String,
        seekBar: SeekBar,
        durationText: TextView,
        playPauseButton: ImageButton
    ) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e("VoiceNotePlayer", "File does not exist: $filePath")
            return
        }

        // Stop any existing playback
        if (mediaPlayer != null) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        // Store UI references
        this.seekBar = seekBar
        this.durationText = durationText
        this.playPauseButton = playPauseButton

        // Create new player
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()

            // Configure seekbar
            seekBar.max = duration
            seekBar.progress = 0

            // Update UI
            updateDurationText(0, duration)
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)

            // Set completion listener
            setOnCompletionListener {
                resetPlayback()
            }
        }

        currentlyPlayingPath = filePath
        startProgressUpdates()

        // CRITICAL FIX: We need to set the click listener on the button directly
        // and handle the logic correctly
        playPauseButton.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            } else {
                mediaPlayer?.start()
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                startProgressUpdates()
            }
        }

        // Set up SeekBar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    updateDurationText(progress, mediaPlayer?.duration ?: 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // This method is no longer needed since we handle clicks directly on the button
    // Keep it for compatibility with existing code
    fun togglePlayPause() {
        // Just forward to the click listener if it exists
        buttonClickListener?.invoke()
    }

    private fun startProgressUpdates() {
        handler.removeCallbacksAndMessages(null)

        handler.post(object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val currentPosition = player.currentPosition
                        seekBar?.progress = currentPosition
                        updateDurationText(currentPosition, player.duration)
                        handler.postDelayed(this, 100)
                    }
                }
            }
        })
    }

    private fun updateDurationText(currentMs: Int, totalMs: Int) {
        val currentSec = currentMs / 1000
        val totalSec = totalMs / 1000
        durationText?.text = String.format("%d:%02d", currentSec / 60, currentSec % 60)
    }

    private fun resetPlayback() {
        playPauseButton?.setImageResource(android.R.drawable.ic_media_play)
        seekBar?.progress = 0
        updateDurationText(0, mediaPlayer?.duration ?: 0)
    }

    fun stopPlayback() {
        handler.removeCallbacksAndMessages(null)

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentlyPlayingPath = null

            resetPlayback()
        } catch (e: Exception) {
            Log.e("VoiceNotePlayer", "Error stopping playback: ${e.message}")
        }
    }

    fun release() {
        stopPlayback()
        seekBar = null
        durationText = null
        playPauseButton = null
        buttonClickListener = null
    }
}
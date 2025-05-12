package com.example.chat_application.dataclasses

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


/**
 * Data class representing a Messages
 */
enum class MessageType {
    TEXT,
    VOICE_NOTE,
    IMAGE
}

@Parcelize
data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val readStatus: HashMap<String, Boolean> = HashMap(),
    val messageType: MessageType = MessageType.TEXT,
    val voiceNoteDuration: Int = 0,
    val voiceNoteLocalPath: String = "",
    val voiceNoteBase64: String = "" // Add this new field to store Base64 encoded voice note data
) : Parcelable
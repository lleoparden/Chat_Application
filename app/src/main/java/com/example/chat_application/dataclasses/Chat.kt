package com.example.chat_application.dataclasses

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Chat(
    var id: String = "",
    var name: String = "",
    var lastMessage: String = "",
    var timestamp: Long = 0,
    var unreadCount: Int = 0,
    var participantIds: HashMap<String,Boolean> = HashMap(),
    var type: String = "",
    val displayName: String = ""
): Parcelable {
    fun getEffectiveDisplayName(): String {
        // If displayName is set, use it. Otherwise, fall back to name
        return if (displayName.isNotEmpty()) displayName else name
    }
}
package com.example.chat_application.dataclasses

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Chat(
    var id: String = "",
    var name: String = "",
    var lastMessage: String = "",
    var timestamp: Long = 0,
    var participantIds: HashMap<String,Boolean> = HashMap(),
    var type: String = "",
    val displayName: String = "",
    var unreadCount: Map<String, Int> = emptyMap() // Map of userId to unread count
) : Parcelable {
    fun getUnreadCountForUser(userId: String): Int {
        return unreadCount[userId] ?: 0
    }
    fun getEffectiveDisplayName(): String {
        // If displayName is set, use it. Otherwise, fall back to name
        return if (displayName.isNotEmpty()) displayName else name
    }
}
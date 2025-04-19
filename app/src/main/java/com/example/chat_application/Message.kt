package com.example.chat_application

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val readStatus: Map<String, Boolean> = mapOf()
)
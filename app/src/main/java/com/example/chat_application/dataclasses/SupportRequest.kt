package com.example.chat_application.dataclasses

/**
 * Data class representing a support request
 */
data class SupportRequest(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val message: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = 0,
    val resolved: Boolean = false,
    val adminResponse: String? = null,
    val responseTimestamp: Long? = null
)
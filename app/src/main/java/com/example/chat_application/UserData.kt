package com.example.chat_application

// User data class
data class UserData(
    val uid: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val userDescription: String = "",
    val userStatus: String = "",
    val online: Boolean = false,
    val lastSeen: String = "",
    val profilePictureUrl: String = ""
)
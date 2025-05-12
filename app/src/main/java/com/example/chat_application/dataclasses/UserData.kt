package com.example.chat_application.dataclasses


/**
 * Data class representing a users
 */
data class UserData(
    val uid: String = "",
    var displayName: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val userDescription: String = "",
    val userStatus: String = "",
    val online: Boolean = false,
    val lastSeen: String = "",
    val profilePictureUrl: String = ""
)
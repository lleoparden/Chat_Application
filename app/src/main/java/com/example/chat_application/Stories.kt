package com.example.chat_application

data class Stories(
    val uid: String = "",
    val displayName: String = "",
    val profilePictureUrl: String = "",
    val Stories : List<Story>
)
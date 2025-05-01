package com.example.chat_application

data class Stories(
    var uid: String = "",
    val displayName: String = "",
    val profilePictureUrl: String = "",
    var stories: List<Story>?
)
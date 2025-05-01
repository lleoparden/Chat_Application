package com.example.chat_application

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Stories(
    var uid: String = "",
    val displayName: String = "",
    val profilePictureUrl: String = "",
    var stories: List<Story>?
) : Parcelable
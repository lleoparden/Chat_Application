package com.example.chat_application.dataclasses

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Story (
    val imageurl : String = "",
    val storyCaption : String = "",
    val uploadedAt : String = "",
) : Parcelable




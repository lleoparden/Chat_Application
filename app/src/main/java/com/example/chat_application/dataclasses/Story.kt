package com.example.chat_application.dataclasses

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


/**
 * Data class representing a story
 */
@Parcelize
data class Story(
    val imageurl: String = "",
    val storyCaption: String = "",
    val uploadedAt: String = "",
) : Parcelable
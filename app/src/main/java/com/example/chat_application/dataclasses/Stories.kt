package com.example.chat_application.dataclasses

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


/**
 * Data class representing a Stories
 */
@Parcelize
data class Stories(
    var uid: String = "",
    val displayName: String = "",
    val profilePictureUrl: String = "",
    var stories: List<Story>?
) : Parcelable
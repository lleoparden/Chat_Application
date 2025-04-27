package com.example.chat_application

import android.content.ContentValues
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.firestore.FirebaseFirestore

object UserSettings {
    var theme: Int = R.style.defaultTheme
    lateinit var userId: String
}

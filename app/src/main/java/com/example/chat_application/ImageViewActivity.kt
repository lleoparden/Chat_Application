package com.example.chat_application

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class ImageViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)

        val imageUrl = intent.getStringExtra("image_url")
        val imageView = findViewById<ImageView>(R.id.fullScreenImageView)

        if (imageUrl != null) {
            Glide.with(this)
                .load(imageUrl)
                .into(imageView)
        }

        imageView.setOnClickListener { finish() }
    }
}

package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class UserProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.user_profile)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener {
            navigateBack()
        }
    }

    private fun navigateBack() {
        val sourceActivity = intent.getStringExtra("came_from")

        if (sourceActivity == "ChatRoom") {
            val chatObject = intent.getParcelableExtra<Chat>("CHAT_OBJECT")
            if (chatObject != null) {
                val intent = Intent(this, ChatRoomActivity::class.java).apply {
                    putExtra("CHAT_OBJECT", chatObject)
                }
                startActivity(intent)
            } else {
                // Fallback to main activity if chat object is missing
                startActivity(Intent(this, MainActivity::class.java))
            }
        } else {
            // Default to AddNewChatActivity or whatever the previous screen should be
            startActivity(Intent(this, AddNewChatActivity::class.java))
        }

        finish()
    }
}
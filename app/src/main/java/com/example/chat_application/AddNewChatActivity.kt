package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class AddNewChatActivity  : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.add_new_chat)


        val backButton = findViewById<Toolbar>(R.id.toolbar)

        backButton.setNavigationOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

//        val intent = Intent(this, UserProfileActivity::class.java).apply {
//            putExtra("came from", "add new chat" )
//        }
//        startActivity(intent)
//        finish()
    }
}

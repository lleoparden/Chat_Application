package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class EditProfileActivity  : AppCompatActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.edit_profile)


        val backButton = findViewById<Toolbar>(R.id.toolbar)

        backButton.setNavigationOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }


    }
}
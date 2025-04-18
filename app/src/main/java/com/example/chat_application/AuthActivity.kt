package com.example.chat_application

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button


class AuthActivity : AppCompatActivity() {

    private lateinit var toMainPage : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.auth_mainpage)

        toMainPage = findViewById(R.id.button)
        toMainPage.setOnClickListener{
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

}
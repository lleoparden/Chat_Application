package com.example.chat_application

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView


class AuthActivity : AppCompatActivity() {

    private lateinit var toMainPage : TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.login_page)

        toMainPage = findViewById(R.id.switchSignInButton)
        toMainPage.setOnClickListener{
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

}
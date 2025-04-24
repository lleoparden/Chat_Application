package com.example.chat_application

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var nameEditText: TextInputEditText
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var readReceiptsSwitch: SwitchCompat
    private lateinit var saveButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.account_settings)

        // Initialize views
        nameEditText = findViewById(R.id.nameEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        readReceiptsSwitch = findViewById(R.id.readReceiptsSwitch)
        saveButton = findViewById(R.id.saveButton)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("UserSettings", MODE_PRIVATE)

        val backButton = findViewById<Toolbar>(R.id.toolbar)

        backButton.setNavigationOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

        // Load saved data if available
        loadSavedData()

        // Handle the Save button click
        saveButton.setOnClickListener {
            saveAccountSettings()
        }
    }

    private fun saveAccountSettings() {
        val name = nameEditText.text.toString()
        val phone = phoneEditText.text.toString()
        val password = passwordEditText.text.toString()
        val readReceiptsEnabled = readReceiptsSwitch.isChecked

        // Save settings in SharedPreferences
        val editor = sharedPreferences.edit()
        editor.putString("name", name)
        editor.putString("phone", phone)
        editor.putString("password", password)
        editor.putBoolean("readReceipts", readReceiptsEnabled)
        editor.apply()

        // Show success message
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
    }

    private fun loadSavedData() {
        val savedName = sharedPreferences.getString("name", "")
        val savedPhone = sharedPreferences.getString("phone", "")
        val savedPassword = sharedPreferences.getString("password", "")
        val savedReadReceipts = sharedPreferences.getBoolean("readReceipts", true)

        // Load saved data into UI components
        nameEditText.setText(savedName)
        phoneEditText.setText(savedPhone)
        passwordEditText.setText(savedPassword)
        readReceiptsSwitch.isChecked = savedReadReceipts
    }
}

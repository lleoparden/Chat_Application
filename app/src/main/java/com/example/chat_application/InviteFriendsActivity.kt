package com.example.chat_application

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class InviteFriendsActivity : AppCompatActivity() {

    private lateinit var inviteLinkText: TextView
    private lateinit var copyLinkButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge support
        enableEdgeToEdge()
        setContentView(R.layout.invite_friends)

        // Toolbar setup
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Invite Friends"
        }
        toolbar.setNavigationOnClickListener { onBackPressed() }

        // Views
        inviteLinkText = findViewById(R.id.inviteLinkText)
        copyLinkButton = findViewById(R.id.copyLinkButton)

        // Copy & Share link
        copyLinkButton.setOnClickListener {
            val link = inviteLinkText.text.toString()
            // Copy to clipboard
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Invite Link", link)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()

            // Share via chooser
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
            }
            startActivity(Intent.createChooser(shareIntent, "Share invite link via"))
        }
    }
}

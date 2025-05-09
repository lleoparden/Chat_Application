package com.example.chat_application

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.provider.MediaStore
import com.example.chat_application.dataclasses.UserSettings

class ChatWallpaperActivity : AppCompatActivity() {

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        private const val PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 100
    }

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var lightThemeRadio: RadioButton
    private lateinit var darkThemeRadio: RadioButton
    private lateinit var systemThemeRadio: RadioButton
    private lateinit var selectedWallpaperPreview: ImageView
    private lateinit var selectFromGalleryButton: Button
    private lateinit var removeWallpaper: Button
    private lateinit var noWallpaperSelected: TextView

    private var selectedWallpaperUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat_wallpaper)

        initViews()

        val backButton = findViewById<Toolbar>(R.id.toolbar)
        backButton.setNavigationOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
        }

        loadSavedPreferences()
        setupClickListeners()
    }

    private fun initViews() {
        themeRadioGroup = findViewById(R.id.themeRadioGroup)
        lightThemeRadio = findViewById(R.id.lightThemeRadio)
        darkThemeRadio = findViewById(R.id.darkThemeRadio)
        systemThemeRadio = findViewById(R.id.systemThemeRadio)
        selectedWallpaperPreview = findViewById(R.id.selectedWallpaperPreview)
        selectFromGalleryButton = findViewById(R.id.selectFromGalleryButton)
        noWallpaperSelected = findViewById(R.id.noWallpaperSelected)
        removeWallpaper = findViewById(R.id.removeWallpaper)
    }

    private fun loadSavedPreferences() {
        // Theme
        when (UserSettings.theme) {
            "light" -> lightThemeRadio.isChecked = true
            "dark" -> darkThemeRadio.isChecked = true
            else -> systemThemeRadio.isChecked = true
        }

        // Wallpaper
        val savedWallpaperPath = UserSettings.getChatWallpaper()
        if (savedWallpaperPath != null) {
            try {
                val wallpaperFile = java.io.File(savedWallpaperPath)
                if (wallpaperFile.exists()) {
                    selectedWallpaperUri = Uri.fromFile(wallpaperFile)
                    selectedWallpaperPreview.setImageURI(selectedWallpaperUri)
                    noWallpaperSelected.visibility = View.GONE
                    removeWallpaper.visibility = View.VISIBLE
                } else {
                    noWallpaperSelected.visibility = View.VISIBLE
                    removeWallpaper.visibility = View.GONE
                }
            } catch (e: Exception) {
                noWallpaperSelected.visibility = View.VISIBLE
                removeWallpaper.visibility = View.GONE
            }
        } else {
            noWallpaperSelected.visibility = View.VISIBLE
            removeWallpaper.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.lightThemeRadio -> "light"
                R.id.darkThemeRadio -> "dark"
                else -> "system"
            }

            val currentTheme = UserSettings.theme

            if (newTheme != currentTheme) {
                UserSettings.theme = newTheme
                UserSettings.saveSettings(this)
                applyTheme(newTheme)
            }
        }

        selectFromGalleryButton.setOnClickListener {
            if (checkStoragePermission()) {
                openGallery()
            } else {
                requestStoragePermission()
            }
        }

        removeWallpaper.setOnClickListener {
            removeWallpaperImage()
        }
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                PERMISSION_REQUEST_READ_EXTERNAL_STORAGE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery()
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Cannot access gallery.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedWallpaperUri = data.data

            selectedWallpaperUri?.let { uri ->
                UserSettings.saveChatWallpaper(this, uri) { success ->
                    if (success) {
                        selectedWallpaperPreview.setImageURI(uri)
                        noWallpaperSelected.visibility = View.GONE
                        removeWallpaper.visibility = View.VISIBLE
                        Toast.makeText(this, "Wallpaper saved successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to save wallpaper", Toast.LENGTH_SHORT).show()
                        noWallpaperSelected.visibility = View.VISIBLE
                        removeWallpaper.visibility = View.GONE
                    }
                }
            }
            UserSettings.saveSettings(this)
        }
    }

    private fun removeWallpaperImage() {
        if (UserSettings.removeChatWallpaper(this)) {
            selectedWallpaperPreview.setImageResource(R.drawable.circle2) // fallback default
            selectedWallpaperUri = null
            noWallpaperSelected.visibility = View.VISIBLE
            removeWallpaper.visibility = View.GONE
            Toast.makeText(this, "Wallpaper removed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to remove wallpaper", Toast.LENGTH_SHORT).show()
        }
    }
}
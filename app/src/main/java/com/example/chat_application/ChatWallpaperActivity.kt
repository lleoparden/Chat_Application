package com.example.chat_application

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class ChatWallpaperActivity : AppCompatActivity() {

    companion object {
        private const val PREF_NAME = "chat_wallpaper_preferences"
        private const val KEY_THEME = "selected_theme"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val THEME_LIGHT = "light"
        private const val THEME_DARK = "dark"
        private const val THEME_SYSTEM = "system"
    }

    private lateinit var themeRadioGroup: RadioGroup
    private lateinit var lightThemeRadio: RadioButton
    private lateinit var darkThemeRadio: RadioButton
    private lateinit var systemThemeRadio: RadioButton
    private lateinit var selectedWallpaperPreview: ImageView
    private lateinit var selectFromGalleryButton: Button
    private lateinit var noWallpaperSelected: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var storageRef: StorageReference
    private var selectedWallpaperUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedWallpaperUri = it
            selectedWallpaperPreview.setImageURI(it) // Show preview immediately
            noWallpaperSelected.visibility = View.GONE
            uploadWallpaperToFirebase(it)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openGallery()
        } else {
            Toast.makeText(this, "Permission denied. Cannot access gallery.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.chat_wallpaper)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        database = FirebaseDatabase.getInstance().reference
        storageRef = FirebaseStorage.getInstance().reference

        // Initialize shared preferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Initialize views
        initViews()

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.activityright, R.anim.activityoutright)
            finish()
        }

        // Load saved preferences
        loadSavedPreferences()

        // Set up click listeners
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
    }

    private fun loadSavedPreferences() {
        // Load theme preference
        val savedTheme = sharedPreferences.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        when (savedTheme) {
            THEME_LIGHT -> lightThemeRadio.isChecked = true
            THEME_DARK -> darkThemeRadio.isChecked = true
            else -> systemThemeRadio.isChecked = true
        }
        applyTheme(savedTheme)

        // Load wallpaper URI if exists
        val savedWallpaperUri = sharedPreferences.getString(KEY_WALLPAPER_URI, null)
        if (savedWallpaperUri != null) {
            try {
                selectedWallpaperUri = Uri.parse(savedWallpaperUri)
                selectedWallpaperPreview.setImageURI(selectedWallpaperUri)
                noWallpaperSelected.visibility = View.GONE
            } catch (e: Exception) {
                noWallpaperSelected.visibility = View.VISIBLE
                Toast.makeText(this, "Failed to load saved wallpaper", Toast.LENGTH_SHORT).show()
            }
        } else {
            noWallpaperSelected.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        // Theme selection listener
        themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.lightThemeRadio -> THEME_LIGHT
                R.id.darkThemeRadio -> THEME_DARK
                else -> THEME_SYSTEM
            }
            sharedPreferences.edit().putString(KEY_THEME, theme).apply()
            applyTheme(theme)
        }

        // Gallery button click listener
        selectFromGalleryButton.setOnClickListener {
            if (checkStoragePermission()) {
                openGallery()
            } else {
                requestStoragePermission()
            }
        }
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun checkStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (shouldShowRequestPermissionRationale(permission)) {
            Toast.makeText(
                this,
                "Storage permission is needed to select a wallpaper",
                Toast.LENGTH_LONG
            ).show()
        }
        requestPermissionLauncher.launch(permission)
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun uploadWallpaperToFirebase(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val fileRef = storageRef.child("wallpapers/$userId/${System.currentTimeMillis()}.jpg")
        selectFromGalleryButton.isEnabled = false // Disable button during upload
        fileRef.putFile(uri)
            .addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    saveWallpaperToDatabase(downloadUri.toString())
                    Toast.makeText(this, "Wallpaper uploaded successfully", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to upload wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                selectFromGalleryButton.isEnabled = true // Re-enable button
            }
    }

    private fun saveWallpaperToDatabase(uri: String) {
        val userId = auth.currentUser?.uid ?: return
        database.child("users").child(userId).child("wallpaper").setValue(uri)
            .addOnSuccessListener {
                sharedPreferences.edit().putString(KEY_WALLPAPER_URI, uri).apply()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save wallpaper to database: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
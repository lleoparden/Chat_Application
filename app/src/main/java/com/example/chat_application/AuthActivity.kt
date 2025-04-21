    package com.example.chat_application

    import android.annotation.SuppressLint
    import android.content.ClipDescription
    import android.content.ContentValues.TAG
    import android.content.Intent
    import android.os.Bundle
    import android.widget.Button
    import androidx.activity.enableEdgeToEdge
    import androidx.appcompat.app.AppCompatActivity
    import android.util.Log
    import android.widget.EditText
    import android.widget.TextView
    import android.widget.Toast
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.firestore.FirebaseFirestore
    import org.json.JSONArray
    import org.json.JSONObject
    import java.io.File
    import java.io.FileReader
    import java.io.FileWriter

    class AuthActivity : AppCompatActivity() {
        // Current view state tracking
        private var currentView = "AUTH" // AUTH, LOGIN, SIGNUP

        // User data storage for registration process
        private var userName: String = ""
        private var userPhone: String = ""
        private var userPassword: String = ""

        // Firestore instance
        private lateinit var db: FirebaseFirestore

        // JSON file for local storage
        private lateinit var localUsersFile: File

        // User data class
        data class UserData(
            val uid: String,
            val displayName: String,
            val phoneNumber: String,
            val password: String,
            val userDescription: String = "",
            val userStatus: String = "",
            val online: Boolean
        )

        @SuppressLint("MissingInflatedId")
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(UserSettings.theme)
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()

            // Initialize Firebase components
            if (resources.getBoolean(R.bool.firebaseOn)) {
                initializeFirebase()
            }

            // Initialize local storage file
            initializeLocalStorage()

            // Check if user is already logged in
            checkUserLoggedIn()
        }

        // Firebase initialization
        private fun initializeFirebase() {
            db = FirebaseFirestore.getInstance()
        }

        // Local storage initialization
        private fun initializeLocalStorage() {
            localUsersFile = File(filesDir, "local_users.json")
            if (!localUsersFile.exists()) {
                try {
                    localUsersFile.createNewFile()
                    saveUsersToJson(emptyList())
                    Log.d(TAG, "Created new local users file at: ${localUsersFile.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating local users file", e)
                }
            }
        }

        private fun checkUserLoggedIn() {
            // Check shared preferences for user ID
            val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
            val userId = prefs.getString("userId", null)

            if (userId != null) {
                // First check if the user exists locally
                if (checkUserExistsLocally(userId)) {
                    Log.d(TAG, "User found locally, logging in")
                    navigateToMainActivity(userId)
                    return
                }

                // If not found locally, verify in Firestore
                if (resources.getBoolean(R.bool.firebaseOn)) {
                    verifyUserInFirestore(userId)
                }
            } else {
                // No user ID stored, show authentication view
                showAuthView()
            }
        }

        // Firebase verification
        private fun verifyUserInFirestore(userId: String) {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // User exists in Firestore, save locally for future
                        val userData = document.data
                        val userToSave = UserData(
                            uid = userId,
                            displayName = userData?.get("displayName") as? String ?: "",
                            phoneNumber = userData?.get("phoneNumber") as? String ?: "",
                            password = userData?.get("password") as? String ?: "",
                            userDescription = userData?.get("userDescription") as? String ?: "",
                            userStatus = userData?.get("userStatus") as? String ?: "",
                            online = userData?.get("online") as? Boolean ?: false
                        )


                        saveUserToLocalStorage(userToSave)
                        navigateToMainActivity(userId)
                    } else {
                        // User ID in preferences but not in Firestore, clear and show auth
                        clearUserSession()
                        showAuthView()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking user login status", e)
                    showAuthView()
                }
        }

        private fun clearUserSession() {
            val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
            prefs.edit().remove("userId").apply()
        }

        private fun showAuthView() {
            // Set initial view
            setContentView(R.layout.authentication_page)
            setupAuthView()
        }

        private fun setupAuthView() {
            val toSignInPage = findViewById<Button>(R.id.goToLogin)
            val toSignUpPage = findViewById<Button>(R.id.goToSignup)

            toSignInPage.setOnClickListener {
                setContentView(R.layout.login_page)
                currentView = "LOGIN"
                setupLoginView()
            }

            toSignUpPage.setOnClickListener {
                setContentView(R.layout.signup_page)
                currentView = "SIGNUP"
                setupSignupView()
            }
        }

        private fun setupLoginView() {
            val number = findViewById<EditText>(R.id.signInPhone)
            val password = findViewById<EditText>(R.id.signInPassword)
            val logInButton = findViewById<Button>(R.id.logInButton)
            val switchLayout = findViewById<TextView>(R.id.switchSignInButton)

            logInButton.setOnClickListener {
                val phoneNumber = number.text.toString().trim()
                val userPassword = password.text.toString().trim()

                if (phoneNumber.isEmpty() || userPassword.isEmpty()) {
                    showToast("Please fill all fields")
                    return@setOnClickListener
                }

                // Format phone number if needed
                val formattedPhone = formatPhoneNumber(phoneNumber)
                userPhone = formattedPhone
                this.userPassword = userPassword

                // Attempt local login first
                if (validateLoginLocally(formattedPhone, userPassword)) {
                    return@setOnClickListener
                }

                // If local login fails, check database
                if (resources.getBoolean(R.bool.firebaseOn)) {
                    validateLoginRemote(formattedPhone, userPassword)
                }
            }

            // Switch to signup
            switchLayout.setOnClickListener {
                setContentView(R.layout.signup_page)
                currentView = "SIGNUP"
                setupSignupView()
            }
        }

        private fun setupSignupView() {
            val name = findViewById<EditText>(R.id.signUpName)
            val number = findViewById<EditText>(R.id.signInPhone)
            val password = findViewById<EditText>(R.id.signInPassword)
            val signUpButton = findViewById<Button>(R.id.signUpButton)
            val switchLayout = findViewById<TextView>(R.id.switchSignInButton)

            signUpButton.setOnClickListener {
                val nameText = name.text.toString().trim()
                val phoneNumber = number.text.toString().trim()
                val passwordText = password.text.toString().trim()

                if (nameText.isEmpty() || phoneNumber.isEmpty() || passwordText.isEmpty()) {
                    showToast("Please fill all fields")
                    return@setOnClickListener
                }

                // Store user data for registration
                userName = nameText
                userPhone = formatPhoneNumber(phoneNumber)
                userPassword = passwordText

                // Check if user already exists locally with this phone number
                if (checkPhoneExistsLocally(userPhone)) {
                    showToast("An account with this phone number already exists locally")
                    return@setOnClickListener
                }

                // Check if user exists in the database
                if (resources.getBoolean(R.bool.firebaseOn)) {
                    checkUserExistsRemote(userPhone)
                }
            }

            // Switch to login
            switchLayout.setOnClickListener {
                setContentView(R.layout.login_page)
                currentView = "LOGIN"
                setupLoginView()
            }
        }

        private fun formatPhoneNumber(phoneNumber: String): String {
            // This is just a simple example - adjust to your requirements
            var formattedNumber = phoneNumber

            // If doesn't start with +, assume it's a local number and add country code
            if (!formattedNumber.startsWith("+")) {
                formattedNumber = "+1$formattedNumber" // Assuming US (+1), change as needed
            }

            return formattedNumber
        }

        private fun showToast(message: String) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        //-----------------------------
        // Local Storage Functions
        //-----------------------------

        private fun getLocalUsers(): List<UserData> {
            if (!localUsersFile.exists() || localUsersFile.length() == 0L) {
                return emptyList()
            }

            return try {
                val fileContent = FileReader(localUsersFile).use { it.readText() }

                // If file is empty or not proper JSON array, return empty list
                if (fileContent.isBlank() || !fileContent.trim().startsWith("[")) {
                    return emptyList()
                }

                val usersList = mutableListOf<UserData>()
                val jsonArray = JSONArray(fileContent)

                for (i in 0 until jsonArray.length()) {
                    val jsonUser = jsonArray.getJSONObject(i)
                    val user = UserData(
                        uid = jsonUser.getString("uid"),
                        displayName = jsonUser.getString("displayName"),
                        phoneNumber = jsonUser.getString("phoneNumber"),
                        password = jsonUser.getString("password"),
                        userDescription = jsonUser.optString("userDescription", ""),
                        userStatus = jsonUser.optString("userStatus", ""),
                        online = jsonUser.optBoolean("online", false)
                    )

                    usersList.add(user)
                }

                usersList
            } catch (e: Exception) {
                Log.e(TAG, "Error reading local users file", e)
                emptyList()
            }
        }

        private fun saveUsersToJson(users: List<UserData>) {
            try {
                val jsonArray = JSONArray()

                users.forEach { userData ->
                    val jsonUser = JSONObject().apply {
                        put("uid", userData.uid)
                        put("displayName", userData.displayName)
                        put("phoneNumber", userData.phoneNumber)
                        put("password", userData.password)
                        put("userDescription", userData.userDescription)
                        put("userStatus", userData.userStatus)
                        put("online", userData.online)

                    }
                    jsonArray.put(jsonUser)
                }

                FileWriter(localUsersFile).use { writer ->
                    writer.write(jsonArray.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving users to local storage", e)
            }
        }

        private fun saveUserToLocalStorage(userData: UserData) {
            val users = getLocalUsers().toMutableList()

            // Check if user already exists
            val existingUserIndex = users.indexOfFirst { it.uid == userData.uid }
            if (existingUserIndex >= 0) {
                users[existingUserIndex] = userData
            } else {
                users.add(userData)
            }

            saveUsersToJson(users)
        }

        private fun checkUserExistsLocally(userId: String): Boolean {
            return getLocalUsers().any { it.uid == userId }
        }

        private fun checkPhoneExistsLocally(phoneNumber: String): Boolean {
            return getLocalUsers().any { it.phoneNumber == phoneNumber }
        }

        private fun validateLoginLocally(phoneNumber: String, password: String): Boolean {
            val users = getLocalUsers()
            val matchingUser = users.find { it.phoneNumber == phoneNumber && it.password == password }

            if (matchingUser != null) {
                // Local login successful
                showToast("Login successful (local)")

                // Store the user ID for session management
                saveUserSession(matchingUser.uid)

                navigateToMainActivity(matchingUser.uid)
                return true
            }

            return false
        }

        private fun saveUserSession(userId: String) {
            val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
            prefs.edit().putString("userId", userId).apply()
        }

        //-----------------------------
        // Firebase Remote Functions
        //-----------------------------

        private fun checkUserExistsRemote(phoneNumber: String) {
            db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        showToast("An account with this phone number already exists")
                    } else {
                        registerUser()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking if user exists", e)
                    showToast("Database error: ${e.message}")
                }
        }

        private fun registerUser() {
            // Generate a unique ID for the user
            val userId = generateUserId()

            // Create user data
            val userData = createUserData(userId)

            // Create a UserData object for local storage
            val localUserData = UserData(
                uid = userId,
                displayName = userName,
                phoneNumber = userPhone,
                password = userPassword,
                userDescription = "",
                userStatus = "",
                online = true
            )


            // Save to local storage first
            saveUserToLocalStorage(localUserData)

            // Save user session
            saveUserSession(userId)

            // Then attempt to save to Firebase
            if (resources.getBoolean(R.bool.firebaseOn)) {
                saveUserToFirebase(userData, userId)
            }

            // Note: Even if Firebase save fails, user can still log in using local data
            showToast("Account created successfully (saved locally)")
        }

        private fun generateUserId(): String {
            return db.collection("users").document().id
        }

        private fun createUserData(userId: String): HashMap<String, String> {
            return hashMapOf(
                "uid" to userId,
                "displayName" to userName,
                "phoneNumber" to userPhone,
                "password" to userPassword
            )
        }

        private fun saveUserToFirebase(userData: HashMap<String, String>, userId: String) {
            db.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener {
                    handleSuccessfulRegistration(userData, userId)
                }
                .addOnFailureListener { e ->
                    handleRegistrationFailure(e)
                }
        }

        private fun handleSuccessfulRegistration(userData: HashMap<String, String>, userId: String) {
            showToast("Account created successfully")

            // Save user data locally
            saveUserToLocalStorage(
                UserData(
                    uid = userId,
                    displayName = userData["displayName"] ?: "",
                    phoneNumber = userData["phoneNumber"] ?: "",
                    password = userData["password"] ?: "",
                    userDescription = userData["userDescription"] ?: "",
                    userStatus = userData["userStatus"] ?: "",
                    online = userData["online"]?.toBoolean() ?: false
                )
            )


            // Save user session
            saveUserSession(userId)

            navigateToMainActivity(userId)
        }

        private fun handleRegistrationFailure(e: Exception) {
            showToast("Failed to create profile: ${e.message}")
            Log.e(TAG, "Error creating user profile", e)
        }

        private fun validateLoginRemote(phoneNumber: String, password: String) {
            db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    handleLoginQueryResult(documents, phoneNumber, password)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error validating login", e)
                    showToast("Database error: ${e.message}")
                }
        }

        private fun handleLoginQueryResult(documents: com.google.firebase.firestore.QuerySnapshot, phoneNumber: String, password: String) {
            if (!documents.isEmpty) {
                // User found, check password
                var isPasswordCorrect = false
                var userId = ""
                var displayName = ""

                for (document in documents) {
                    val userPassword = document.getString("password")
                    if (userPassword == password) {
                        isPasswordCorrect = true
                        userId = document.getString("uid") ?: document.id
                        displayName = document.getString("displayName") ?: ""
                        break
                    }
                }

                if (isPasswordCorrect) {
                    handleSuccessfulRemoteLogin(userId, displayName, phoneNumber, password)
                } else {
                    showToast("Incorrect password")
                }
            } else {
                showToast("No account found with this phone number")
            }
        }

        private fun handleSuccessfulRemoteLogin(userId: String, displayName: String, phoneNumber: String, password: String) {
            showToast("Login successful")

            // Save user data locally for future logins
            saveUserToLocalStorage(
                UserData(
                    uid = userId,
                    displayName = displayName,
                    phoneNumber = phoneNumber,
                    password = password,
                    userDescription = "",
                    userStatus = "",
                    online = true
                )
            )


            // Store the user ID for session management
            saveUserSession(userId)

            navigateToMainActivity(userId)
        }

        private fun navigateToMainActivity(userId: String) {
            UserSettings.userId = userId
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Close AuthActivity so user can't go back
        }
    }
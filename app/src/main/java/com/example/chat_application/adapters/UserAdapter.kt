package com.example.chat_application.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.R
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.HelperFunctions

class UserAdapter(
    private val users: List<UserData>,
    private val existingChatUserIds: Set<String>,  // Pass existing chat IDs as a parameter
    private val listener: OnUserClickListener
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val TAG = "UserAdapter"

    init {
        Log.d(TAG, "Adapter initialized with ${users.size} users")
    }

    interface OnUserClickListener {
        fun onUserClick(user: UserData)
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val phoneTextView: TextView = itemView.findViewById(R.id.phoneTextView)
        private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        private val profileImageView : ImageView = itemView.findViewById(R.id.profileImageView)
        private val chatStatusView: TextView = itemView.findViewById(R.id.chatStatusTextView)

        init {
            Log.v(TAG, "ViewHolder initialized")
        }

        fun bind(user: UserData) {
            Log.d(TAG, "Binding user: ${user.displayName}, phone: ${user.phoneNumber}")

            try {
                nameTextView.text = user.displayName
                phoneTextView.text = user.phoneNumber
                statusTextView.text = user.userStatus
                HelperFunctions.loadImageFromUrl(user.profilePictureUrl, profileImageView)


                // Show chat status by checking the set instead of a property on UserData
                val hasExistingChat = existingChatUserIds.contains(user.uid)
                if (hasExistingChat) {
                    chatStatusView.visibility = View.VISIBLE
                    chatStatusView.text = "Existing Chat"
                } else {
                    chatStatusView.visibility = View.GONE
                }

                // Set click listener
                itemView.setOnClickListener {
                    Log.i(TAG, "User clicked: ${user.displayName} (${user.uid})")
                    listener.onUserClick(user)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error binding user data", e)
            }
        }


    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        Log.d(TAG, "Creating new ViewHolder")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        Log.d(TAG, "Binding ViewHolder at position $position")
        try {
            holder.bind(users[position])
        } catch (e: Exception) {
            Log.e(TAG, "Error binding view holder at position $position", e)
        }
    }

    override fun getItemCount(): Int {
        val count = users.size
        Log.v(TAG, "getItemCount(): $count")
        return count
    }

    fun getUserAtPosition(position: Int): UserData? {
        return try {
            users[position].also {
                Log.d(TAG, "Retrieved user at position $position: ${it.displayName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving user at position $position", e)
            null
        }
    }
}
package com.example.chat_application

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UserAdapter(
    private val users: List<UserData>,
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

        init {
            Log.v(TAG, "ViewHolder initialized")
        }

        fun bind(user: UserData) {
            Log.d(TAG, "Binding user: ${user.displayName}, phone: ${user.phoneNumber}")

            try {
                nameTextView.text = user.displayName
                phoneTextView.text = user.phoneNumber

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
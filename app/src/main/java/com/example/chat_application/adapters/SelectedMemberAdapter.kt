package com.example.chat_application.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.chat_application.R
import com.example.chat_application.dataclasses.UserData

class SelectedMemberAdapter(
    private val selectedUsers: List<UserData>,
    private val onRemoveClicked: (UserData) -> Unit
) : RecyclerView.Adapter<SelectedMemberAdapter.SelectedMemberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedMemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_member, parent, false)
        return SelectedMemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: SelectedMemberViewHolder, position: Int) {
        val user = selectedUsers[position]
        holder.bind(user)
    }

    override fun getItemCount(): Int = selectedUsers.size

    inner class SelectedMemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val memberNameTextView: TextView = itemView.findViewById(R.id.memberNameTextView)
        private val memberImageView: ImageView = itemView.findViewById(R.id.memberProfileImageView)
        private val removeButton: ImageView = itemView.findViewById(R.id.removeMemberButton)

        fun bind(user: UserData) {
            // Set user name
            memberNameTextView.text = user.displayName

            // Load profile image
            if (user.profilePictureUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(user.profilePictureUrl)
                    .placeholder(R.drawable.ic_person)
                    .circleCrop()
                    .into(memberImageView)
            } else {
                // Use default image if no profile picture
                memberImageView.setImageResource(R.drawable.ic_person)
            }

            // Set click listener for remove button
            removeButton.setOnClickListener {
                onRemoveClicked(user)
            }
        }
    }
}
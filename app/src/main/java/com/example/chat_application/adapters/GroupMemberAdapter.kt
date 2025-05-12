package com.example.chat_application.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.HelperFunctions
import com.example.chat_application.R
import com.example.chat_application.dataclasses.UserData

private val TAG = "GroupMemberAdapter"

// Adapter for the group members RecyclerView
class GroupMemberAdapter(
    private val members: List<UserData>,
    private val onItemClick: (UserData) -> Unit
) : RecyclerView.Adapter<GroupMemberAdapter.MemberViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.bind(member)
    }

    override fun getItemCount(): Int = members.size

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val memberName: TextView = itemView.findViewById(R.id.memberNameText)
        private val memberAvatar: ImageView = itemView.findViewById(R.id.memberAvatar)

        fun bind(user: UserData) {
            memberName.text = user.displayName

            // Load profile picture if available
            if (user.profilePictureUrl.isNotEmpty()) {
                HelperFunctions.loadImageFromUrl(user.profilePictureUrl, memberAvatar)
            }

            itemView.setOnClickListener {
                onItemClick(user)
            }
        }
    }
}
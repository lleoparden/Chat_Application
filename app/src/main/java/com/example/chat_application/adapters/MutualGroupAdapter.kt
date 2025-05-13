package com.example.chat_application.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.chat_application.HelperFunctions
import com.example.chat_application.R
import com.example.chat_application.dataclasses.Chat

private val TAG = "MutualGroupAdapter"

// Adapter for the group Groups RecyclerView
class MutualGroupAdapter(
    private val Groups: List<Chat>,
    private val onItemClick: (Chat) -> Unit
) : RecyclerView.Adapter<MutualGroupAdapter.GroupViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mutual_group, parent, false)
        return GroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val Group = Groups[position]
        holder.bind(Group)
    }

    override fun getItemCount(): Int = Groups.size

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val groupName: TextView = itemView.findViewById(R.id.groupNameTextView)
        private val groupImg: ImageView = itemView.findViewById(R.id.groupImageView)

        fun bind(Group: Chat) {
            groupName.text = Group.displayName

            // Load profile picture if available

            HelperFunctions.getGroupPfp(Group.id) { url ->
                if (url != null) {
                    HelperFunctions.loadImageFromUrl(url, groupImg)
                }
            }



            itemView.setOnClickListener {
                onItemClick(Group)
            }
        }
    }
}
package org.koitharu.kotatsu.comments.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.koitharu.kotatsu.comments.Comment
// import coil.load // For image loading - add Coil dependency if not present
// import org.koitharu.kotatsu.R // For placeholder drawable if using Coil
import org.koitharu.kotatsu.comments.databinding.ItemCommentBinding // Make sure your item layout is named item_comment.xml

class CommentAdapter(
    private val onReportClicked: (Comment) -> Unit,
    private val onDeleteClicked: (Comment) -> Unit // TODO: Add logic to show delete only for moderators or own comments
) : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = getItem(position)
        holder.bind(comment, onReportClicked, onDeleteClicked)
    }

    inner class CommentViewHolder(private val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: Comment, onReportClicked: (Comment) -> Unit, onDeleteClicked: (Comment) -> Unit) {
            binding.usernameTextView.text = comment.userName
            binding.commentTextView.text = comment.text

            // Avatar Image Loading (example with Coil - ensure Coil is in your build.gradle)
            // if (comment.avatarUrl != null) {
            //     binding.avatarImageView.load(comment.avatarUrl) {
            //         crossfade(true)
            //         placeholder(R.drawable.ic_avatar_placeholder) // Create a placeholder drawable
            //         error(R.drawable.ic_avatar_placeholder) // Create an error drawable
            //     }
            // } else {
            //     binding.avatarImageView.setImageResource(R.drawable.ic_avatar_placeholder) // Default placeholder
            // }

            binding.reportButton.setOnClickListener {
                onReportClicked(comment)
            }

            // TODO: Implement logic for showing/hiding delete button based on user role
            // For now, it's always visible. In a real app, you'd check if the current user
            // is a moderator or the author of the comment.
            // binding.deleteButton.visibility = if (canDelete(comment)) View.VISIBLE else View.GONE
            // binding.deleteButton.setOnClickListener { onDeleteClicked(comment) }
            // Assuming item_comment.xml has a deleteButton, if not, this part needs adjustment or a different UI approach
        }
    }
}

class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
    override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean {
        return oldItem == newItem
    }
}

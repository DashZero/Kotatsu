package org.koitharu.kotatsu.threads

import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.databinding.ItemThreadCommentBinding
import kotlin.math.max

private const val INDENT_DP = 24

class ThreadCommentAdapter : ListAdapter<ThreadCommentItem, ThreadCommentViewHolder>(DiffCallback) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadCommentViewHolder {
		val binding = ItemThreadCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ThreadCommentViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ThreadCommentViewHolder, position: Int) {
		holder.bind(getItem(position))
	}

	private object DiffCallback : DiffUtil.ItemCallback<ThreadCommentItem>() {
		override fun areItemsTheSame(oldItem: ThreadCommentItem, newItem: ThreadCommentItem): Boolean =
			oldItem.comment.id == newItem.comment.id

		override fun areContentsTheSame(oldItem: ThreadCommentItem, newItem: ThreadCommentItem): Boolean =
			oldItem == newItem
	}
}

class ThreadCommentViewHolder(
	private val binding: ItemThreadCommentBinding,
) : RecyclerView.ViewHolder(binding.root) {

	fun bind(item: ThreadCommentItem) = with(binding) {
		val comment = item.comment
		imageAvatar.setImageAsync(comment.user.avatar)
		val context = root.context
		textMeta.text = context.getString(
			R.string.thread_comment_meta_format,
			comment.user.name,
			DateUtils.getRelativeTimeSpanString(
				comment.createdAt * 1000L,
				System.currentTimeMillis(),
				DateUtils.MINUTE_IN_MILLIS,
				DateUtils.FORMAT_ABBREV_RELATIVE,
			),
		)
		val html = comment.commentHtml ?: comment.comment
		textBody.text = HtmlCompat.fromHtml(html.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY)
		textBody.movementMethod = LinkMovementMethod.getInstance()
		val likeDisplay = max(comment.likeCount, if (comment.isLiked) 1 else 0)
		chipLikes.isVisible = likeDisplay > 0
		if (chipLikes.isVisible) {
			chipLikes.text = likeDisplay.toString()
		}
		val params = root.layoutParams as ViewGroup.MarginLayoutParams
		val indentPx = (INDENT_DP * item.depth * root.resources.displayMetrics.density).toInt()
		params.marginStart = indentPx
		root.layoutParams = params
	}
}

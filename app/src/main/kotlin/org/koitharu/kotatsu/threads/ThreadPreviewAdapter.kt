package org.koitharu.kotatsu.threads

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.databinding.ItemThreadPreviewBinding

class ThreadPreviewAdapter(
	private val onItemClick: (AniListThread) -> Unit,
) : ListAdapter<AniListThread, ThreadPreviewViewHolder>(DiffCallback) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadPreviewViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = ItemThreadPreviewBinding.inflate(inflater, parent, false)
		return ThreadPreviewViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ThreadPreviewViewHolder, position: Int) {
		val thread = getItem(position)
		holder.bind(thread)
		holder.itemView.setOnClickListener {
			onItemClick(thread)
		}
	}

	private object DiffCallback : DiffUtil.ItemCallback<AniListThread>() {
		override fun areItemsTheSame(oldItem: AniListThread, newItem: AniListThread): Boolean = oldItem.id == newItem.id

		override fun areContentsTheSame(oldItem: AniListThread, newItem: AniListThread): Boolean = oldItem == newItem
	}
}

class ThreadPreviewViewHolder(
	private val binding: ItemThreadPreviewBinding,
) : RecyclerView.ViewHolder(binding.root) {

	fun bind(thread: AniListThread) = with(binding) {
		textTitle.text = thread.title
		val context = root.context
		val replies = context.resources.getQuantityString(R.plurals.thread_replies_count, thread.replyCount, thread.replyCount)
		textMeta.text = context.getString(R.string.thread_meta_format, thread.user.name, replies)
		val referenceTime = when {
			thread.repliedAt != null && thread.repliedAt > 0L -> thread.repliedAt
			thread.updatedAt > 0L -> thread.updatedAt
			else -> thread.createdAt
		}
		textTimestamp.text = DateUtils.getRelativeTimeSpanString(
			referenceTime * 1000L,
			System.currentTimeMillis(),
			DateUtils.MINUTE_IN_MILLIS,
			DateUtils.FORMAT_ABBREV_RELATIVE,
		)
		chipLocked.isVisible = thread.isLocked
	}
}

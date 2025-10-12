package org.koitharu.kotatsu.threads

import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.databinding.ItemThreadHeaderBinding

class ThreadHeaderAdapter : RecyclerView.Adapter<ThreadHeaderViewHolder>() {

	var thread: AniListThread? = null
		set(value) {
			field = value
			notifyDataSetChanged()
		}

	override fun getItemCount(): Int = if (thread == null) 0 else 1

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadHeaderViewHolder {
		val binding = ItemThreadHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ThreadHeaderViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ThreadHeaderViewHolder, position: Int) {
		thread?.let(holder::bind)
	}
}

class ThreadHeaderViewHolder(
	private val binding: ItemThreadHeaderBinding,
) : RecyclerView.ViewHolder(binding.root) {

	fun bind(thread: AniListThread) = with(binding) {
		textTitle.text = thread.title
		val context = root.context
		val replies = context.resources.getQuantityString(R.plurals.thread_replies_count, thread.replyCount, thread.replyCount)
		val referenceTime = when {
			thread.repliedAt != null && thread.repliedAt > 0L -> thread.repliedAt
			thread.updatedAt > 0L -> thread.updatedAt
			else -> thread.createdAt
		}
		val updated = DateUtils.getRelativeTimeSpanString(
			referenceTime * 1000L,
			System.currentTimeMillis(),
			DateUtils.MINUTE_IN_MILLIS,
			DateUtils.FORMAT_ABBREV_RELATIVE,
		)
		textMeta.text = context.getString(
			R.string.thread_header_meta_format,
			thread.user.name,
			replies,
			updated,
		)
		val html = thread.bodyHtml ?: thread.body
		textBody.text = HtmlCompat.fromHtml(html.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY)
		textBody.movementMethod = LinkMovementMethod.getInstance()
	}
}

package org.koitharu.kotatsu.reviews

import android.text.format.DateUtils
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.databinding.ItemReviewBinding

class ReviewListAdapter : ListAdapter<AniListReview, ReviewViewHolder>(DiffCallback) {

	var viewerId: Long? = null

	override fun submitList(list: List<AniListReview>?) {
		Log.d(TAG, "Adapter submitList size=${list?.size ?: 0}")
		super.submitList(list)
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
		val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		return ReviewViewHolder(binding)
	}

	override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
		holder.bind(getItem(position), viewerId)
	}

	companion object {
		private const val TAG = "KotatsuReviews"

		private object DiffCallback : DiffUtil.ItemCallback<AniListReview>() {
			override fun areItemsTheSame(oldItem: AniListReview, newItem: AniListReview): Boolean = oldItem.id == newItem.id

			override fun areContentsTheSame(oldItem: AniListReview, newItem: AniListReview): Boolean = oldItem == newItem
		}
	}
}

class ReviewViewHolder(
	private val binding: ItemReviewBinding,
) : RecyclerView.ViewHolder(binding.root) {

	private val linkMovementMethod by lazy(LazyThreadSafetyMode.NONE) {
		LinkMovementMethod.getInstance()
	}

	fun bind(review: AniListReview, viewerId: Long?) = with(binding) {
		imageAvatar.setImageAsync(review.user.avatar)
		textName.text = review.user.name
		textTimestamp.text = DateUtils.getRelativeTimeSpanString(
			review.createdAt * 1000L,
			System.currentTimeMillis(),
			DateUtils.MINUTE_IN_MILLIS,
			DateUtils.FORMAT_ABBREV_RELATIVE,
		)
		textSummary.text = review.summary
		val bodyContent = review.bodyHtml ?: review.body
		textBody.text = androidx.core.text.HtmlCompat.fromHtml(bodyContent, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT)
		textBody.movementMethod = linkMovementMethod
		val scoreValue = review.score
		if (scoreValue != null) {
			textScore.isVisible = true
			textScore.text = binding.root.context.getString(R.string.review_score_format, scoreValue)
		} else {
			textScore.isGone = true
		}
		val ratingValue = review.rating
		if (ratingValue != null && ratingValue > 0) {
			textRating.isVisible = true
			textRating.text = ratingValue.toString()
		} else {
			textRating.isGone = true
		}
		val context = binding.root.context
		val defaultColor = MaterialColors.getColor(cardBubble, com.google.android.material.R.attr.colorSurfaceContainerHigh)
		val ownColor = MaterialColors.getColor(cardBubble, com.google.android.material.R.attr.colorPrimaryContainer)
		cardBubble.setCardBackgroundColor(
			if (viewerId != null && review.user.id == viewerId) ownColor else defaultColor,
		)
	}
}

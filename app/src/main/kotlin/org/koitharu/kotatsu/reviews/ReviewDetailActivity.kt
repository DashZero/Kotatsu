package org.koitharu.kotatsu.reviews

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.databinding.ActivityReviewDetailBinding

@AndroidEntryPoint
class ReviewDetailActivity : AppCompatActivity() {

	private val viewModel: ReviewDetailViewModel by viewModels()
	private lateinit var binding: ActivityReviewDetailBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityReviewDetailBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setupInteractions()
		observeState()
		observeMessages()
		collectEvents()
	}

	private fun setupInteractions() {
		binding.buttonLike.setOnClickListener {
			viewModel.toggleRating(ReviewRating.UP_VOTE)
		}
		binding.buttonDislike.setOnClickListener {
			viewModel.toggleRating(ReviewRating.DOWN_VOTE)
		}
		binding.buttonEdit.setOnClickListener {
			editReview()
		}
		binding.buttonDelete.setOnClickListener {
			confirmDelete()
		}
	}

	private fun observeState() {
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.state.collect { renderState(it) }
			}
		}
	}

	private fun collectEvents() {
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.events.collect { event ->
					when (event) {
						is ReviewDetailEvent.ReviewUpdated -> {
							setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_REVIEW, event.review))
						}
						is ReviewDetailEvent.ReviewDeleted -> {
							setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT_DELETED_ID, event.reviewId))
							finish()
						}
					}
				}
			}
		}
	}

	private fun observeMessages() {
		viewModel.messages.observeEvent(this) { message ->
			when (message) {
				is ReviewMessage.Resource -> Snackbar.make(binding.scrollView, getString(message.resId, *message.formatArgs), Snackbar.LENGTH_SHORT).show()
				is ReviewMessage.Plain -> Snackbar.make(binding.scrollView, message.value, Snackbar.LENGTH_SHORT).show()
			}
		}
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(binding.scrollView, null))
	}

	private fun renderState(state: ReviewDetailUiState) = with(binding) {
		progress.isVisible = state.isBusy
		buttonLike.isEnabled = !state.isBusy
		buttonDislike.isEnabled = !state.isBusy
		buttonEdit.isVisible = state.isOwner
		buttonDelete.isVisible = state.isOwner
		buttonEdit.isEnabled = state.isOwner && !state.isBusy
		buttonDelete.isEnabled = state.isOwner && !state.isBusy
		val review = state.review
		imageAvatar.setImageAsync(review.user.avatar)
		textAuthor.text = review.user.name
		textTimestamp.text = android.text.format.DateUtils.getRelativeTimeSpanString(
			review.createdAt * 1000L,
			System.currentTimeMillis(),
			android.text.format.DateUtils.MINUTE_IN_MILLIS,
			android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
		)
		textSummary.text = review.summary
		textBody.text = HtmlCompat.fromHtml(review.bodyHtml ?: review.body, HtmlCompat.FROM_HTML_MODE_COMPACT)
		textBody.movementMethod = LinkMovementMethod.getInstance()
		val scoreValue = review.score
		textScore.isVisible = scoreValue != null
		if (scoreValue != null) {
			textScore.text = getString(R.string.review_score_format, scoreValue)
		}
		val totalVotes = review.ratingAmount ?: 0
		textRating.isVisible = totalVotes > 0
		if (totalVotes > 0) {
			textRating.text = getString(R.string.review_likes_count, totalVotes)
		}
		groupRating.clearChecked()
		when (review.userRating) {
			ReviewRating.UP_VOTE -> groupRating.check(buttonLike.id)
			ReviewRating.DOWN_VOTE -> groupRating.check(buttonDislike.id)
			else -> Unit
		}
	}

	private fun editReview() {
		val current = viewModel.state.value.review
		lifecycleScope.launch {
			val draft = viewModel.loadDraft()
			showReviewEditorDialog(
				context = this@ReviewDetailActivity,
				scope = lifecycleScope,
				layoutInflater = layoutInflater,
				existing = current,
				draft = draft,
				renderMarkdown = { markdown -> viewModel.renderMarkdown(markdown) },
				onSaveDraft = { summary, body, score -> viewModel.saveDraft(summary, body, score) },
				onSubmit = { summary, body, score -> viewModel.submitReview(summary, body, score) },
			)
		}
	}

	private fun confirmDelete() {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.review_delete_confirm)
			.setMessage(R.string.review_delete_confirm_message)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.review_delete) { _, _ -> viewModel.deleteReview() }
			.show()
	}

	companion object {
		const val EXTRA_REVIEW = "extra_review"
		const val EXTRA_MEDIA_ID = "extra_media_id"
		const val EXTRA_VIEWER_ID = "extra_viewer_id"
		const val EXTRA_RESULT_REVIEW = "extra_result_review"
		const val EXTRA_RESULT_DELETED_ID = "extra_result_deleted_id"

		fun createIntent(context: Context, nav: ReviewDetailNav): Intent = Intent(context, ReviewDetailActivity::class.java)
			.putExtra(EXTRA_REVIEW, nav.review)
			.putExtra(EXTRA_MEDIA_ID, nav.mediaId)
			.putExtra(EXTRA_VIEWER_ID, nav.viewerId)
	}
}

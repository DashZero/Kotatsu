package org.koitharu.kotatsu.reviews

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.EventFlow
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import javax.inject.Inject

@HiltViewModel
class ReviewDetailViewModel @Inject constructor(
	private val repository: ReviewRepository,
	savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

	private val initialReview: AniListReview = requireNotNull(savedStateHandle.get<AniListReview>(ReviewDetailActivity.EXTRA_REVIEW))
	private val mediaId: Int = savedStateHandle.get<Int>(ReviewDetailActivity.EXTRA_MEDIA_ID) ?: initialReview.mediaId
	private val viewerId: Long? = savedStateHandle.get<Long?>(ReviewDetailActivity.EXTRA_VIEWER_ID)

	private val _state = MutableStateFlow(
		ReviewDetailUiState(
			review = initialReview,
			isOwner = viewerId == initialReview.user.id,
			isBusy = false,
		),
	)
	val state: StateFlow<ReviewDetailUiState> = _state

	private val _events = MutableEventFlow<ReviewDetailEvent>()
	val events: EventFlow<ReviewDetailEvent>
		get() = _events

	private val _messages = MutableEventFlow<ReviewMessage>()
	val messages: EventFlow<ReviewMessage>
		get() = _messages

	fun toggleRating(requested: ReviewRating) {
		val current = _state.value.review
		val target = if (current.userRating == requested) ReviewRating.NO_VOTE else requested
		sendVote(target)
	}

	private fun sendVote(target: ReviewRating) {
		if (_state.value.isBusy) {
			return
		}
		val original = _state.value.review
		_state.value = _state.value.copy(isBusy = true, review = original.copy(userRating = target))
		launchJob {
			try {
				val updated = repository.rateReview(original.id, target)
				_state.value = _state.value.copy(isBusy = false, review = updated)
				_events.call(ReviewDetailEvent.ReviewUpdated(updated))
			} catch (error: Throwable) {
				_state.value = _state.value.copy(isBusy = false, review = original)
				_messages.call(ReviewMessage.Resource(R.string.review_like_error))
				throw error
			}
		}
	}

	fun deleteReview() {
		if (_state.value.isBusy) {
			return
		}
		val review = _state.value.review
		_state.value = _state.value.copy(isBusy = true)
		launchJob {
			try {
				repository.deleteReview(mediaId, review.id)
				repository.clearDraft(mediaId)
				_events.call(ReviewDetailEvent.ReviewDeleted(review.id))
			} finally {
				_state.value = _state.value.copy(isBusy = false)
			}
		}
	}

	fun submitReview(summary: String, body: String, score: Int) {
		if (_state.value.isBusy) {
			return
		}
		_state.value = _state.value.copy(isBusy = true)
		launchJob {
			try {
				val feed = repository.saveReview(mediaId, summary, body, score)
				repository.clearDraft(mediaId)
				val updated = feed.reviews.firstOrNull { it.id == _state.value.review.id }
				val review = updated ?: feed.myReview ?: _state.value.review
				_state.value = _state.value.copy(isBusy = false, review = review)
				_events.call(ReviewDetailEvent.ReviewUpdated(review))
			} catch (error: Throwable) {
				_state.value = _state.value.copy(isBusy = false)
				throw error
			}
		}
	}

	suspend fun loadDraft(): ReviewDraft? = repository.loadDraft(mediaId)

	fun saveDraft(summary: String, body: String, score: Int) {
		launchJob {
			repository.saveDraft(mediaId, summary, body, score)
			_messages.call(ReviewMessage.Resource(R.string.review_draft_saved))
		}
	}

	fun clearDraft() {
		launchJob {
			repository.clearDraft(mediaId)
		}
	}

	suspend fun renderMarkdown(markdown: String): Result<String> = runCatching {
		repository.renderMarkdown(markdown)
	}
}

data class ReviewDetailUiState(
	val review: AniListReview,
	val isOwner: Boolean,
	val isBusy: Boolean,
)

sealed class ReviewDetailEvent {
	data class ReviewUpdated(val review: AniListReview) : ReviewDetailEvent()
	data class ReviewDeleted(val reviewId: Long) : ReviewDetailEvent()
}

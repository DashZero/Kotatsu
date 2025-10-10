package org.koitharu.kotatsu.reviews

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.exception.GraphQLException
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.scrobbling.anilist.domain.AniListScrobbler
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.koitharu.kotatsu.scrobbling.common.data.ScrobblingEntity
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val FIRST_PAGE = 1
private const val DEFAULT_PAGE_SIZE = 10

@Singleton
class ReviewRepository @Inject constructor(
	private val client: AniListReviewClient,
	private val database: MangaDatabase,
	private val aniListScrobbler: AniListScrobbler,
) {

	private var cachedMediaId: Int? = null
	private var cachedReviews: MutableList<AniListReview> = mutableListOf()
	private var cachedPage: Int = 0
	private var cachedHasNext: Boolean = false
	private var cachedViewer: AniListViewer? = null
	private var cachedMyReview: AniListReview? = null
	private var cachedMediaListId: Int? = null
	private var currentMangaId: Long = 0L

	suspend fun resolveAccess(mangaId: Long): ReviewAccess = withContext(Dispatchers.IO) {
		currentMangaId = mangaId
		if (!aniListScrobbler.isEnabled) {
			return@withContext ReviewAccess.NotAuthorized
		}
		val entity = database.getScrobblingDao().find(ScrobblerService.ANILIST.id, mangaId)
			?: return@withContext ReviewAccess.NotTracked
		cachedMediaListId = entity.id
		return@withContext ReviewAccess.Granted(
			mediaId = entity.targetId.toInt(),
			mediaListId = entity.id,
		)
	}

	suspend fun refresh(access: ReviewAccess.Granted): ReviewFeed {
		cachedMediaListId = access.mediaListId
		return refreshInternal(access.mediaId, allowRetry = true)
	}

	private suspend fun refreshInternal(mediaId: Int, allowRetry: Boolean): ReviewFeed {
		return try {
			if (cachedMediaId != mediaId) {
				resetCache(mediaId)
			}
			val viewer = ensureViewer()
			val page = client.fetchReviews(mediaId, FIRST_PAGE, DEFAULT_PAGE_SIZE)
			Log.d(TAG, "Fetched ${page.reviews.size} reviews for mediaId=$mediaId (allowRetry=$allowRetry)")
			if (page.reviews.isEmpty() && allowRetry) {
				resolveMediaIdFallback(mediaId)?.let { fallbackId ->
					Log.d(TAG, "Retrying reviews with fallback mediaId=$fallbackId")
					return refreshInternal(fallbackId, allowRetry = false)
				}
			}
			cachedPage = page.currentPage
			cachedHasNext = page.hasNextPage
			cachedReviews = page.reviews.toMutableList()
			Log.d(TAG, "Cached reviews size=${cachedReviews.size}")
			cachedMyReview = try {
				viewer?.let { user ->
					client.fetchUserReview(mediaId, user.id)
				}
			} catch (e: IOException) {
				if (e.message?.contains("404") == true) {
					null
				} else {
					throw e
				}
			}
			integrateMyReview()
			val feed = buildFeed()
			Log.d(TAG, "Returning feed with ${feed.reviews.size} reviews")
			feed
		} catch (error: GraphQLException) {
			if (!allowRetry) {
				throw error
			}
			Log.w(TAG, "GraphQL error for mediaId=$mediaId, attempting fallback", error)
			val fallbackId = resolveMediaIdFallback(mediaId) ?: throw error
			Log.d(TAG, "Retrying after GraphQL error with fallback mediaId=$fallbackId")
			return refreshInternal(fallbackId, allowRetry = false)
		} catch (error: Throwable) {
			Log.e(TAG, "Failed to build review feed for mediaId=$mediaId (Ask Gemini)", error)
			throw error
		}
	}

	suspend fun loadMore(): ReviewFeed {
		val mediaId = cachedMediaId ?: throw IllegalStateException("Reviews not initialized")
		if (!cachedHasNext) {
			return buildFeed()
		}
		val nextPage = cachedPage + 1
		val page = client.fetchReviews(mediaId, nextPage, DEFAULT_PAGE_SIZE)
		cachedPage = page.currentPage
		cachedHasNext = page.hasNextPage
		val myReviewId = cachedMyReview?.id
		if (myReviewId != null) {
			cachedReviews.addAll(page.reviews.filterNot { it.id == myReviewId })
		} else {
			cachedReviews.addAll(page.reviews)
		}
		return buildFeed()
	}

	suspend fun saveReview(mediaId: Int, summary: String, body: String, score: Int): ReviewFeed {
		val review = client.saveReview(mediaId, summary, body, score)
		cachedMyReview = review
		cachedMediaId = mediaId
		removeReviewById(review.id)
		cachedReviews.add(0, review)
		return refreshInternal(mediaId, allowRetry = true)
	}

	suspend fun deleteReview(mediaId: Int, reviewId: Long): ReviewFeed {
		client.deleteReview(reviewId)
		removeReviewById(reviewId)
		cachedMyReview = null
		return refreshInternal(mediaId, allowRetry = true)
	}

	suspend fun renderMarkdown(markdown: String): String = client.renderMarkdown(markdown)

	private suspend fun ensureViewer(): AniListViewer? {
		if (cachedViewer != null) {
			return cachedViewer
		}
		val viewer = client.fetchViewer()
		cachedViewer = viewer
		return viewer
	}

	private fun integrateMyReview() {
		val review = cachedMyReview ?: return
		removeReviewById(review.id)
		cachedReviews.add(0, review)
	}

	private fun removeReviewById(reviewId: Long) {
		val idx = cachedReviews.indexOfFirst { it.id == reviewId }
		if (idx >= 0) {
			cachedReviews.removeAt(idx)
		}
	}

	private fun resetCache(mediaId: Int) {
		cachedMediaId = mediaId
		cachedReviews.clear()
		cachedPage = 0
		cachedHasNext = false
		cachedMyReview = null
	}

	private fun buildFeed(): ReviewFeed {
		return ReviewFeed(
			mediaId = cachedMediaId,
			reviews = cachedReviews.toList(),
			hasNext = cachedHasNext,
			currentPage = cachedPage,
			viewer = cachedViewer,
			myReview = cachedMyReview,
		)
	}

	private suspend fun resolveMediaIdFallback(currentMediaId: Int): Int? {
		val listId = cachedMediaListId ?: return null
		val ref = client.fetchMediaIdByListId(listId) ?: return null
		if (ref.id == currentMediaId) {
			return null
		}
		updateStoredMediaId(ref.id.toLong())
		resetCache(ref.id)
		return ref.id
	}

	private suspend fun updateStoredMediaId(newMediaId: Long) {
		val entity = database.getScrobblingDao().find(ScrobblerService.ANILIST.id, currentMangaId) ?: return
		val updated = ScrobblingEntity(
			scrobbler = entity.scrobbler,
			id = entity.id,
			mangaId = entity.mangaId,
			targetId = newMediaId,
			status = entity.status,
			chapter = entity.chapter,
			comment = entity.comment,
			rating = entity.rating,
		)
		database.getScrobblingDao().upsert(updated)
	}

	companion object {
		private const val TAG = "KotatsuReviews"
	}
}

sealed interface ReviewAccess {
	data object NotAuthorized : ReviewAccess
	data object NotTracked : ReviewAccess
	data class Granted(val mediaId: Int, val mediaListId: Int) : ReviewAccess
}

data class ReviewFeed(
	val mediaId: Int?,
	val reviews: List<AniListReview>,
	val hasNext: Boolean,
	val currentPage: Int,
	val viewer: AniListViewer?,
	val myReview: AniListReview?,
)


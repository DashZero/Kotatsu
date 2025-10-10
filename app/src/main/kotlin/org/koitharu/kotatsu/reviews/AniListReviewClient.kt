package org.koitharu.kotatsu.reviews

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.exception.GraphQLException
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.parseJson
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerType
import javax.inject.Inject
import javax.inject.Singleton

private const val ENDPOINT = "https://graphql.anilist.co"
private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
private const val MAX_RETRY_COUNT = 3
private const val BACKOFF_BASE = 1_000L
private const val HTTP_TOO_MANY_REQUESTS = 429

@Singleton
class AniListReviewClient @Inject constructor(
	@ScrobblerType(ScrobblerService.ANILIST) private val httpClient: OkHttpClient,
) {

	suspend fun fetchViewer(): AniListViewer? {
		val response = execute(
			query = """
				query ViewerShort {
					Viewer {
						id
						name
						avatar {
							medium
						}
					}
				}
			""".trimIndent(),
		)
		val data = response.optJSONObject("data")?.optJSONObject("Viewer") ?: return null
		return data.toViewer()
	}

	suspend fun fetchReviews(mediaId: Int, page: Int, perPage: Int): ReviewPage {
		val response = execute(
			query = """
				query MediaReviews(${'$'}mediaId: Int!, ${'$'}page: Int!, ${'$'}perPage: Int!) {
					Media(id: ${'$'}mediaId) {
						id
						reviews(page: ${'$'}page, perPage: ${'$'}perPage, sort: [RATING_DESC, ID_DESC]) {
							pageInfo {
								currentPage
								hasNextPage
							}
							nodes {
								id
								summary
								bodyHtml: body(asHtml: true)
								body
								score
								rating
								createdAt
								user {
									id
									name
									avatar {
										medium
									}
								}
							}
						}
					}
				}
			""".trimIndent(),
			variables = JSONObject()
				.put("mediaId", mediaId)
				.put("page", page)
				.put("perPage", perPage),
		)
		val data = response.getJSONObject("data").getJSONObject("Media")
		val reviews = data.getJSONObject("reviews")
		val pageInfo = reviews.getJSONObject("pageInfo")
		return ReviewPage(
			reviews = reviews.getJSONArray("nodes").parseReviews(),
			currentPage = pageInfo.getInt("currentPage"),
			hasNextPage = pageInfo.getBoolean("hasNextPage"),
		)
	}

	suspend fun fetchUserReview(mediaId: Int, userId: Long): AniListReview? {
		val response = execute(
			query = """
				query MyReviewSingle(${'$'}mediaId: Int!, ${'$'}userId: Int!) {
					Review(mediaId: ${'$'}mediaId, userId: ${'$'}userId) {
						id
						summary
						bodyHtml: body(asHtml: true)
						body
						score
						rating
						createdAt
						user {
							id
							name
							avatar {
								medium
							}
						}
					}
				}
			""".trimIndent(),
			variables = JSONObject()
				.put("mediaId", mediaId)
				.put("userId", userId),
		)
		val reviewObject = response.optJSONObject("data")?.optJSONObject("Review") ?: return null
		return reviewObject.toReview()
	}

	suspend fun saveReview(mediaId: Int, summary: String, body: String, score: Int): AniListReview {
		val response = execute(
			query = """
				mutation SaveReview(${'$'}mediaId: Int!, ${'$'}summary: String!, ${'$'}body: String!, ${'$'}score: Int!) {
					SaveReview(mediaId: ${'$'}mediaId, summary: ${'$'}summary, body: ${'$'}body, score: ${'$'}score) {
						id
						summary
						bodyHtml: body(asHtml: true)
						body
						score
						rating
						createdAt
						user {
							id
							name
							avatar {
								medium
							}
						}
					}
				}
			""".trimIndent(),
			variables = JSONObject()
				.put("mediaId", mediaId)
				.put("summary", summary)
				.put("body", body)
				.put("score", score),
		)
		val reviewObject = response.getJSONObject("data").getJSONObject("SaveReview")
		return reviewObject.toReview()
	}

	suspend fun deleteReview(reviewId: Long): Boolean {
		val response = execute(
			query = """
				mutation DeleteReview(${'$'}id: Int!) {
					DeleteReview(id: ${'$'}id) {
						deleted
					}
				}
			""".trimIndent(),
			variables = JSONObject().put("id", reviewId),
		)
		return response.getJSONObject("data").getJSONObject("DeleteReview").getBoolean("deleted")
	}

	suspend fun renderMarkdown(markdown: String): String {
		val response = execute(
			query = """
				query ParseMarkdown(${'$'}md: String!) {
					Markdown(markdown: ${'$'}md) {
						html
					}
				}
			""".trimIndent(),
			variables = JSONObject().put("md", markdown),
		)
		return response.getJSONObject("data").getJSONObject("Markdown").getString("html")
	}

	suspend fun fetchMediaIdByListId(mediaListId: Int): AniListMediaRef? {
		val response = execute(
			query = """
				query MediaListEntry(${'$'}id: Int!) {
					MediaList(id: ${'$'}id) {
						mediaId
						media {
							id
							type
						}
					}
				}
			""".trimIndent(),
			variables = JSONObject().put("id", mediaListId),
		)
		val listObject = response.optJSONObject("data")?.optJSONObject("MediaList") ?: return null
		val mediaObject = listObject.optJSONObject("media")
		val mediaId = listObject.optInt("mediaId").takeIf { it != 0 }
			?: mediaObject?.optInt("id")?.takeIf { it != 0 }
			?: return null
		return AniListMediaRef(
			id = mediaId,
			type = mediaObject?.optString("type"),
		)
	}

	private suspend fun execute(query: String, variables: JSONObject? = null): JSONObject {
		val payload = JSONObject()
			.put("query", query)
		if (variables != null) {
			payload.put("variables", variables)
		}
		val requestBody = payload.toString().toRequestBody(MEDIA_TYPE_JSON)
		val request = Request.Builder()
			.url(ENDPOINT)
			.post(requestBody)
			.build()

		var attempt = 0
		var backoffMillis = BACKOFF_BASE
		var lastException: Throwable? = null

		retry@ while (attempt < MAX_RETRY_COUNT) {
			val response = try {
				httpClient.newCall(request).await()
			} catch (io: IOException) {
				lastException = io
				attempt++
				if (attempt >= MAX_RETRY_COUNT) {
					break
				}
				delay(backoffMillis)
				backoffMillis *= 2
				continue
			}

			if (response.code == HTTP_TOO_MANY_REQUESTS) {
				val retryAfter = response.header("Retry-After")
				val remaining = response.header("X-RateLimit-Remaining")
				lastException = AniListRateLimitException(retryAfter, remaining)
				response.close()
				attempt++
				if (attempt >= MAX_RETRY_COUNT) {
					break
				}
				delay(calculateBackoff(retryAfter, backoffMillis))
				backoffMillis *= 2
				continue@retry
			}
			try {
				response.ensureSuccessOrThrow()
				val json = response.parseJson()
				json.optJSONArray("errors")?.let { errors ->
					if (errors.length() > 0) {
						throw GraphQLException(errors)
					}
				}
				return json
			} catch (error: Throwable) {
				response.close()
				throw error
			}
		}
		throw lastException ?: AniListRateLimitException(null, null)
	}

	private fun Response.ensureSuccessOrThrow() {
		if (!isSuccessful) {
			throw IOException("AniList request failed with code $code")
		}
	}

	private fun calculateBackoff(retryAfterHeader: String?, fallback: Long): Long {
		val retryAfter = retryAfterHeader?.toLongOrNull()
		val waitMillis = if (retryAfter != null) {
			retryAfter * 1000L
		} else {
			fallback
		}
		return waitMillis.coerceAtLeast(BACKOFF_BASE)
	}

	private fun JSONObject.toViewer() = AniListViewer(
		id = getLong("id"),
		name = getString("name"),
		avatar = optJSONObject("avatar")?.optString("medium"),
	)

	private fun JSONObject.toReview(): AniListReview = AniListReview(
		id = getLong("id"),
		summary = optString("summary").orEmpty(),
		body = optString("body").orEmpty(),
		bodyHtml = optString("bodyHtml", null)
			?: optString("body", null),
		score = optInt("score").takeIf { has("score") && !isNull("score") },
		rating = optInt("rating").takeIf { has("rating") && !isNull("rating") },
		createdAt = optLong("createdAt", 0L),
		user = getJSONObject("user").toReviewer(),
	)

	private fun JSONObject.toReviewer() = AniListReviewer(
		id = getLong("id"),
		name = getString("name"),
		avatar = optJSONObject("avatar")?.optString("medium"),
	)

	private fun JSONArray.parseReviews(): List<AniListReview> {
		if (length() == 0) {
			return emptyList()
		}
		val result = ArrayList<AniListReview>(length())
		for (index in 0 until length()) {
			val reviewObject = getJSONObject(index)
			result.add(reviewObject.toReview())
		}
		return result
	}
}

data class AniListReviewer(
	val id: Long,
	val name: String,
	val avatar: String?,
)

data class AniListReview(
	val id: Long,
	val summary: String,
	val body: String,
	val bodyHtml: String?,
	val score: Int?,
	val rating: Int?,
	val createdAt: Long,
	val user: AniListReviewer,
)

data class ReviewPage(
	val reviews: List<AniListReview>,
	val currentPage: Int,
	val hasNextPage: Boolean,
)

data class AniListViewer(
	val id: Long,
	val name: String,
	val avatar: String?,
)

data class AniListMediaRef(
	val id: Int,
	val type: String?,
)

class AniListRateLimitException(
	retryAfterHeader: String?,
	remainingHeader: String?,
) : IOException(
	buildString {
		append("AniList rate limit reached")
		if (!retryAfterHeader.isNullOrEmpty()) {
			append(" retryAfter=").append(retryAfterHeader)
		}
		if (!remainingHeader.isNullOrEmpty()) {
			append(" remaining=").append(remainingHeader)
		}
	},
)

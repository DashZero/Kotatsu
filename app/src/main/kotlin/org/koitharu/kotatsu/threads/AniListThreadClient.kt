package org.koitharu.kotatsu.threads

import android.os.Parcelable
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
import org.koitharu.kotatsu.reviews.AniListMediaRef
import org.koitharu.kotatsu.reviews.AniListRateLimitException
import org.koitharu.kotatsu.reviews.AniListReviewer
import org.koitharu.kotatsu.reviews.AniListViewer
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerService
import org.koitharu.kotatsu.scrobbling.common.domain.model.ScrobblerType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.parcelize.Parcelize
import kotlin.math.max

private const val ENDPOINT = "https://graphql.anilist.co"
private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
private const val MAX_RETRY_COUNT = 3
private const val BACKOFF_BASE = 1_000L
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val FIRST_PAGE = 1
private const val DEFAULT_COMMENT_PAGE_SIZE = 20

@Singleton
class AniListThreadClient @Inject constructor(
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

	suspend fun fetchThreads(mediaId: Int, page: Int, perPage: Int, sort: List<String>): ThreadPage {
		val response = execute(
			query = """
				query MediaThreads(${'$'}mediaId: Int!, ${'$'}page: Int!, ${'$'}perPage: Int!, ${'$'}sort: [ThreadSort!]) {
					Page(page: ${'$'}page, perPage: ${'$'}perPage) {
						pageInfo {
							currentPage
							hasNextPage
						}
						threads(mediaId: ${'$'}mediaId, mediaCategory_in: [MANGA], sort: ${'$'}sort) {
							id
							title
							body(asHtml: false)
							replyCount
							viewCount
							createdAt
							repliedAt
							isLocked
							isSticky
							user {
								id
								name
								avatar {
									medium
								}
							}
							mediaCategories {
								id
							}
						}
					}
				}
			""".trimIndent(),
			variables = JSONObject()
				.put("mediaId", mediaId)
				.put("page", page)
				.put("perPage", perPage)
				.put("sort", JSONArray().apply {
					sort.forEach { put(it) }
				}),
		)
		val pageObject = response.getJSONObject("data").getJSONObject("Page")
		val pageInfo = pageObject.getJSONObject("pageInfo")
		return ThreadPage(
			threads = pageObject.getJSONArray("threads").parseThreads(mediaId),
			currentPage = pageInfo.getInt("currentPage"),
			hasNextPage = pageInfo.getBoolean("hasNextPage"),
		)
	}

	suspend fun fetchThread(threadId: Long, page: Int = FIRST_PAGE, perPage: Int = DEFAULT_COMMENT_PAGE_SIZE): ThreadWithComments? {
		val response = execute(
			query = """
				query ThreadDetail(${'$'}id: Int!, ${'$'}page: Int!, ${'$'}perPage: Int!) {
					Thread(id: ${'$'}id) {
						id
						title
						body
						bodyHtml: body(asHtml: true)
						replyCount
						viewCount
						repliedAt
						createdAt
						updatedAt
						isLocked
						isSticky
						user {
							id
							name
							avatar {
								medium
							}
						}
						comments(page: ${'$'}page, perPage: ${'$'}perPage) {
							pageInfo {
								currentPage
								hasNextPage
							}
							nodes {
								id
								comment
								commentHtml: comment(asHtml: true)
								createdAt
								updatedAt
								likeCount
								isLiked
								user {
									id
									name
									avatar { medium }
								}
								childComments {
									id
									threadId
									comment
									commentHtml: comment(asHtml: true)
									likeCount
									isLiked
									createdAt
									updatedAt
									isLocked
									user {
										id
										name
										avatar { medium }
									}
								}
							}
						}
					}
				}
			""".trimIndent(),
			variables = JSONObject()
				.put("id", threadId.toInt())
				.put("page", page)
				.put("perPage", perPage),
		)
		val threadObject = response.optJSONObject("data")?.optJSONObject("Thread") ?: return null
		val commentsObject = threadObject.optJSONObject("comments")
		val commentNodes = commentsObject?.optJSONArray("nodes")
		val pageInfo = commentsObject?.optJSONObject("pageInfo")
		val commentPage = ThreadCommentPage(
			threadId = threadId,
			comments = commentNodes?.parseCommentNodes(threadId) ?: emptyList(),
			currentPage = pageInfo?.optInt("currentPage") ?: page,
			hasNextPage = pageInfo?.optBoolean("hasNextPage") ?: false,
		)
		return ThreadWithComments(
			thread = threadObject.toThread(),
			commentPage = commentPage,
		)
	}

	suspend fun fetchThreadComments(threadId: Long, page: Int, perPage: Int): ThreadCommentPage {
		val response = execute(
			query = """
				query ThreadComments(${'$'}id: Int!, ${'$'}page: Int!, ${'$'}perPage: Int!) {
					Thread(id: ${'$'}id) {
						comments(page: ${'$'}page, perPage: ${'$'}perPage) {
							pageInfo {
								currentPage
								hasNextPage
							}
							nodes {
								id
								comment
								commentHtml: comment(asHtml: true)
								likeCount
								isLiked
								createdAt
								updatedAt
								isLocked
								user {
									id
									name
									avatar {
										medium
									}
								}
								childComments {
									id
									threadId
									comment
									commentHtml: comment(asHtml: true)
									likeCount
									isLiked
									createdAt
									updatedAt
									isLocked
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
				}
			""".trimIndent(),
			variables = JSONObject()
				.put("id", threadId.toInt())
				.put("page", page)
				.put("perPage", perPage),
		)
		val threadObject = response.getJSONObject("data").getJSONObject("Thread")
		val commentsObject = threadObject.getJSONObject("comments")
		val pageInfo = commentsObject.getJSONObject("pageInfo")
		return ThreadCommentPage(
			threadId = threadId,
			comments = commentsObject.getJSONArray("nodes").parseCommentNodes(threadId),
			currentPage = pageInfo.getInt("currentPage"),
			hasNextPage = pageInfo.getBoolean("hasNextPage"),
		)
	}

	suspend fun saveThread(mediaId: Int, title: String, body: String): AniListThread {
		val response = execute(
			query = """
				mutation SaveThread(${'$'}mediaId: Int!, ${'$'}title: String!, ${'$'}body: String!) {
					SaveThread(
						mediaCategories: [${'$'}mediaId],
						title: ${'$'}title,
						body: ${'$'}body
					) {
						id
						title
						body
						bodyHtml: body(asHtml: true)
						replyCount
						viewCount
						replyCommentId
						createdAt
						updatedAt
						isLocked
						isSticky
						user {
							id
							name
							avatar {
								medium
							}
						}
						mediaCategories {
							id
						}
					}
				}
			""".trimIndent(),
			variables = JSONObject()
				.put("mediaId", mediaId)
				.put("title", title)
				.put("body", body),
		)
		val threadObject = response.getJSONObject("data").getJSONObject("SaveThread")
		return threadObject.toThread(defaultMediaId = mediaId)
	}

	suspend fun saveThreadComment(
		threadId: Long,
		parentCommentId: Long?,
		comment: String,
	): AniListThreadComment {
	val variables = JSONObject()
		.put("threadId", threadId.toInt())
		.put("comment", comment)
	if (parentCommentId != null) {
		variables.put("parentCommentId", parentCommentId.toInt())
	}
		val response = execute(
			query = """
				mutation SaveThreadComment(${'$'}threadId: Int!, ${'$'}comment: String!, ${'$'}parentCommentId: Int) {
					SaveThreadComment(
						threadId: ${'$'}threadId,
						parentCommentId: ${'$'}parentCommentId,
						comment: ${'$'}comment
					) {
						id
						threadId
						comment
						commentHtml: comment(asHtml: true)
						likeCount
						isLiked
						createdAt
						updatedAt
						isLocked
						user {
							id
							name
							avatar {
								medium
							}
						}
						childComments {
							id
							threadId
							comment
							commentHtml: comment(asHtml: true)
							likeCount
							isLiked
							createdAt
							updatedAt
							isLocked
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
			""".trimIndent(),
			variables = variables,
		)
		val commentObject = response.getJSONObject("data").getJSONObject("SaveThreadComment")
		return commentObject.toComment(threadId, parentCommentId)
	}

	suspend fun deleteThreadComment(commentId: Long): Boolean {
		val response = execute(
			query = """
				mutation DeleteThreadComment(${'$'}id: Int!) {
					DeleteThreadComment(id: ${'$'}id) {
						deleted
					}
				}
			""".trimIndent(),
		variables = JSONObject().put("id", commentId.toInt()),
	)
		return response.getJSONObject("data").getJSONObject("DeleteThreadComment").getBoolean("deleted")
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
		val request = Request.Builder()
			.url(ENDPOINT)
			.post(payload.toString().toRequestBody(MEDIA_TYPE_JSON))
			.build()
		var lastError: Throwable? = null
		for (attempt in 0..MAX_RETRY_COUNT) {
			try {
				return httpClient.newCall(request).await().use(::parseResponse)
			} catch (rateLimit: AniListRateLimitException) {
				lastError = rateLimit
				val retryAfter = calculateBackoff(rateLimit.message, BACKOFF_BASE * max(1, attempt).toLong())
				delay(retryAfter)
			} catch (error: Throwable) {
				lastError = error
				if (attempt == MAX_RETRY_COUNT) {
					throw error
				}
				delay(BACKOFF_BASE * (attempt + 1L))
			}
		}
		throw lastError ?: IllegalStateException("AniListThreadClient ended without response")
	}

	private fun parseResponse(response: Response): JSONObject {
		if (response.code == HTTP_TOO_MANY_REQUESTS) {
			throw AniListRateLimitException(
				retryAfterHeader = response.header("retry-after"),
				remainingHeader = response.header("x-ratelimit-remaining"),
			)
		}
		response.parseJson().let { body ->
			val errors = body.optJSONArray("errors")
			if (errors != null && errors.length() > 0) {
				throw GraphQLException(errors)
			}
			return body
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

	private fun JSONObject.toViewer(): AniListViewer = AniListViewer(
		id = getLong("id"),
		name = getString("name"),
		avatar = optJSONObject("avatar")?.optString("medium"),
	)

	private fun JSONObject.toThread(defaultMediaId: Int? = null): AniListThread = AniListThread(
		id = getLong("id"),
	mediaCategoryIds = optJSONArray("mediaCategories")?.let { array ->
		val ids = mutableListOf<Int>()
		for (index in 0 until array.length()) {
			val item = array.getJSONObject(index)
			val id = item.optInt("id", defaultMediaId ?: 0)
			if (id != 0) {
				ids += id
			}
		}
		if (ids.isNotEmpty()) ids else defaultMediaId?.let { listOf(it) } ?: emptyList()
		} ?: listOfNotNull(defaultMediaId),
		title = optString("title"),
		body = optString("body"),
		bodyHtml = optString("bodyHtml", null),
		replyCount = optInt("replyCount"),
		viewCount = optInt("viewCount"),
		repliedAt = optLong("repliedAt").takeIf { has("repliedAt") },
		replyCommentId = optLong("replyCommentId").takeIf { has("replyCommentId") },
		createdAt = optLong("createdAt"),
		updatedAt = optLong("updatedAt"),
		isLocked = optBoolean("isLocked"),
		isSticky = optBoolean("isSticky"),
		user = optJSONObject("user")?.toReviewer()
			?: AniListReviewer(id = 0, name = "", avatar = null),
	)

	private fun JSONObject.toReviewer(): AniListReviewer = AniListReviewer(
		id = optLong("id"),
		name = optString("name"),
		avatar = optJSONObject("avatar")?.optString("medium"),
	)

	private fun JSONArray.parseThreads(mediaId: Int): List<AniListThread> {
		if (length() == 0) {
			return emptyList()
		}
		val result = ArrayList<AniListThread>(length())
		for (index in 0 until length()) {
			val node = getJSONObject(index)
			result.add(node.toThread(defaultMediaId = mediaId))
		}
		return result
	}

private fun JSONArray.parseCommentNodes(threadId: Long): List<AniListThreadComment> {
		if (length() == 0) {
			return emptyList()
		}
		val result = ArrayList<AniListThreadComment>(length())
		for (index in 0 until length()) {
			val node = getJSONObject(index)
			result.add(node.toComment(threadId, parentId = null))
		}
		return result
	}

	private fun JSONObject.toComment(threadId: Long, parentId: Long?): AniListThreadComment {
		val commentId = optLong("id")
		val childrenArray = optJSONArray("childComments")
		val children = if (childrenArray != null && childrenArray.length() > 0) {
			val result = ArrayList<AniListThreadComment>(childrenArray.length())
			for (index in 0 until childrenArray.length()) {
				val childObject = childrenArray.getJSONObject(index)
				result.add(childObject.toComment(threadId, parentId = commentId))
			}
			result
		} else {
			emptyList()
		}
		return AniListThreadComment(
			id = commentId,
			threadId = optLong("threadId", threadId),
			parentCommentId = parentId,
			comment = optString("comment"),
			commentHtml = optString("commentHtml", null),
			likeCount = optInt("likeCount"),
			isLiked = optBoolean("isLiked"),
			createdAt = optLong("createdAt"),
			updatedAt = optLong("updatedAt"),
			isLocked = optBoolean("isLocked"),
			user = optJSONObject("user")?.toReviewer()
				?: AniListReviewer(id = 0, name = "", avatar = null),
			children = children,
		)
	}
}

@Parcelize
data class AniListThread(
	val id: Long,
	val mediaCategoryIds: List<Int>,
	val title: String,
	val body: String,
	val bodyHtml: String?,
	val replyCount: Int,
	val viewCount: Int,
	val repliedAt: Long?,
	val replyCommentId: Long?,
	val createdAt: Long,
	val updatedAt: Long,
	val isLocked: Boolean,
	val isSticky: Boolean,
	val user: AniListReviewer,
) : Parcelable

@Parcelize
data class AniListThreadComment(
	val id: Long,
	val threadId: Long,
	val parentCommentId: Long?,
	val comment: String,
	val commentHtml: String?,
	val likeCount: Int,
	val isLiked: Boolean,
	val createdAt: Long,
	val updatedAt: Long,
	val isLocked: Boolean,
	val user: AniListReviewer,
	val children: List<AniListThreadComment>,
) : Parcelable {

	val hasChildren: Boolean
		get() = children.isNotEmpty()
}

enum class ThreadSort(val graphQlValue: String) {
	RECENT_ACTIVITY("REPLIED_AT_DESC"),
	NEWEST("CREATED_AT_DESC"),
	MOST_REPLIES("REPLY_COUNT_DESC"),
}

data class ThreadPage(
	val threads: List<AniListThread>,
	val currentPage: Int,
	val hasNextPage: Boolean,
)

data class ThreadCommentPage(
	val threadId: Long,
	val comments: List<AniListThreadComment>,
	val currentPage: Int,
	val hasNextPage: Boolean,
)

data class ThreadWithComments(
	val thread: AniListThread,
	val commentPage: ThreadCommentPage,
)

package org.koitharu.kotatsu.reviews

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class ReviewDraftStorage @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

	fun save(draft: ReviewDraft) {
		val json = JSONObject()
			.put("mediaId", draft.mediaId)
			.put("userId", draft.userId)
			.put("summary", draft.summary)
			.put("body", draft.body)
			.put("score", draft.score)
			.put("lastEdited", draft.lastEdited)
		prefs.edit()
			.putString(buildKey(draft.mediaId, draft.userId), json.toString())
			.apply()
	}

	fun load(mediaId: Int, userId: Long): ReviewDraft? {
		val raw = prefs.getString(buildKey(mediaId, userId), null) ?: return null
		return runCatching {
			val json = JSONObject(raw)
			ReviewDraft(
				mediaId = json.getInt("mediaId"),
				userId = json.getLong("userId"),
				summary = json.optString("summary").orEmpty(),
				body = json.optString("body").orEmpty(),
				score = json.optInt("score"),
				lastEdited = json.optLong("lastEdited"),
			)
		}.getOrNull()
	}

	fun clear(mediaId: Int, userId: Long) {
		prefs.edit()
			.remove(buildKey(mediaId, userId))
			.apply()
	}

	private fun buildKey(mediaId: Int, userId: Long): String = "$userId-$mediaId"

	private companion object {
		private const val PREF_NAME = "review_drafts"
	}
}

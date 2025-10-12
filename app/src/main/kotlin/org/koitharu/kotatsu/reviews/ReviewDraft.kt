package org.koitharu.kotatsu.reviews

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReviewDraft(
	val mediaId: Int,
	val userId: Long,
	val summary: String,
	val body: String,
	val score: Int,
	val lastEdited: Long,
) : Parcelable

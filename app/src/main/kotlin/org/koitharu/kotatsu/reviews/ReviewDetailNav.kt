package org.koitharu.kotatsu.reviews

data class ReviewDetailNav(
    val review: AniListReview,
    val mediaId: Int,
    val viewerId: Long?,
)

package org.koitharu.kotatsu.gdrive

import org.koitharu.kotatsu.data.IFavoriteRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GdriveFavoriteRepository @Inject constructor(
    private val favouritesRepository: FavouritesRepository
) : IFavoriteRepository {

    override suspend fun getAllFavorites(): List<String> {
        return favouritesRepository.getAllManga().map { it.id.toString() }
    }

    override suspend fun addFavorite(mangaId: String) {
        // This function is for updating from the sync, not for local updates.
    }

    override suspend fun removeFavorite(mangaId: String) {
        // This function is for updating from the sync, not for local updates.
    }
}

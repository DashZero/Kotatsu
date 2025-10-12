package org.koitharu.kotatsu.gdrive

import kotlinx.coroutines.flow.first
import org.koitharu.kotatsu.data.ILibraryRepository
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.gdrive.models.LibraryData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GdriveLibraryRepository @Inject constructor(
    private val mangaSourcesRepository: MangaSourcesRepository,
    private val favouritesRepository: FavouritesRepository
) : ILibraryRepository {

    override suspend fun getLibraryData(): LibraryData {
        val sources = mangaSourcesRepository.getEnabledSources().map { it.mangaSource.name }
        val categories = favouritesRepository.observeCategories().first().map { it.title }
        return LibraryData(sources = sources, categories = categories)
    }
}

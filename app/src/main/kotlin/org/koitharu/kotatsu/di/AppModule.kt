package org.koitharu.kotatsu.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.koitharu.kotatsu.data.IAppSettings
import org.koitharu.kotatsu.data.IBookmarkRepository
import org.koitharu.kotatsu.data.IFavoriteRepository
import org.koitharu.kotatsu.data.ILibraryRepository
import org.koitharu.kotatsu.data.IReadingHistoryRepository
import org.koitharu.kotatsu.data.MockAppSettings
import org.koitharu.kotatsu.data.MockBookmarkRepository
import org.koitharu.kotatsu.data.MockFavoriteRepository
import org.koitharu.kotatsu.data.MockLibraryRepository
import org.koitharu.kotatsu.data.MockReadingHistoryRepository
import org.koitharu.kotatsu.gdrive.DriveSyncProvider
import org.koitharu.kotatsu.gdrive.EncryptionUtil
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideReadingHistoryRepository(historyRepository: HistoryRepository): IReadingHistoryRepository {
        return GdriveHistoryRepository(historyRepository)
    }

    @Provides
    @Singleton
    fun provideBookmarkRepository(bookmarksRepository: BookmarksRepository): IBookmarkRepository {
        return GdriveBookmarkRepository(bookmarksRepository)
    }

    @Provides
    @Singleton
    fun provideFavoriteRepository(favouritesRepository: FavouritesRepository): IFavoriteRepository {
        return GdriveFavoriteRepository(favouritesRepository)
    }

    @Provides
    @Singleton
    fun provideAppSettings(appSettings: AppSettings): IAppSettings {
        return GdriveAppSettings(appSettings)
    }

    @Provides
    @Singleton
    fun provideLibraryRepository(
        mangaSourcesRepository: MangaSourcesRepository,
        favouritesRepository: FavouritesRepository
    ): ILibraryRepository {
        return GdriveLibraryRepository(mangaSourcesRepository, favouritesRepository)
    }


    @Provides
    @Singleton
    fun provideEncryptionUtil(@ApplicationContext context: Context): EncryptionUtil {
        return EncryptionUtil(context)
    }

    @Provides
    @Singleton
    fun provideDriveSyncProvider(
        @ApplicationContext context: Context,
        readingHistoryRepository: IReadingHistoryRepository,
        bookmarkRepository: IBookmarkRepository,
        favoriteRepository: IFavoriteRepository,
        appSettings: IAppSettings,
        libraryRepository: ILibraryRepository
    ): DriveSyncProvider {
        return DriveSyncProvider(
            context,
            readingHistoryRepository,
            bookmarkRepository,
            favoriteRepository,
            appSettings,
            libraryRepository
        )
    }
}

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
import org.koitharu.kotatsu.data.HistoryRepository // Import HistoryRepository
import org.koitharu.kotatsu.data.BookmarksRepository // Import BookmarksRepository
import org.koitharu.kotatsu.data.FavouritesRepository // Import FavouritesRepository
import org.koitharu.kotatsu.data.AppSettings // Import AppSettings
import org.koitharu.kotatsu.data.MangaSourcesRepository // Import MangaSourcesRepository
import org.koitharu.kotatsu.gdrive.SyncRegistry

import org.koitharu.kotatsu.gdrive.DriveSyncProvider
import org.koitharu.kotatsu.gdrive.EncryptionUtil
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideReadingHistoryRepository(historyRepository: HistoryRepository): IReadingHistoryRepository {
        // HistoryRepository itself implements IReadingHistoryRepository
        return historyRepository
    }

    @Provides
    @Singleton
    fun provideBookmarkRepository(bookmarksRepository: BookmarksRepository): IBookmarkRepository {
        // Assuming BookmarksRepository implements IBookmarkRepository
        return bookmarksRepository
    }

    @Provides
    @Singleton
    fun provideFavoriteRepository(favouritesRepository: FavouritesRepository): IFavoriteRepository {
        // Assuming FavouritesRepository implements IFavoriteRepository
        return favouritesRepository
    }

    @Provides
    @Singleton
    fun provideAppSettings(appSettings: AppSettings): IAppSettings {
        // Assuming AppSettings implements IAppSettings
        return appSettings
    }

    @Provides
    @Singleton
    fun provideLibraryRepository(
        mangaSourcesRepository: MangaSourcesRepository,
        favouritesRepository: FavouritesRepository
    ): ILibraryRepository {
        // Assuming GdriveLibraryRepository is the correct implementation and it requires these two
        return GdriveLibraryRepository(mangaSourcesRepository, favouritesRepository)
    }

    @Provides
    @Singleton
    fun provideSyncRegistry(): SyncRegistry {
        return SyncRegistry()
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
        // DriveSyncProvider needs I... types
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

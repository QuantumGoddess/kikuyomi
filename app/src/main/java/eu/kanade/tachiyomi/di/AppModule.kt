package eu.kanade.tachiyomi.di

import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import dataaudiobook.Audiobookhistory
import dataaudiobook.Audiobooks
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.audiobook.store.DelayedAudiobookTrackingStore
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.AudiobookChapterCache
import eu.kanade.tachiyomi.data.cache.AudiobookCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.EpisodeCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadCache
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.audiobook.AudiobookExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.audiobook.AndroidAudiobookSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import eu.kanade.tachiyomi.ui.audioplayer.ExternalIntents
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kikuyomi.data.AudiobookDatabase
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import tachiyomi.core.storage.AndroidStorageFolderProvider
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.AudiobookUpdateStrategyColumnAdapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.audiobook.AndroidAudiobookDatabaseHandler
import tachiyomi.data.handlers.audiobook.AudiobookDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.audiobook.service.AudiobookSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.data.download.audiobook.AudiobookDownloadManager
import tachiyomi.source.local.image.audiobook.LocalAudiobookCoverManager
import tachiyomi.source.local.io.audiobook.LocalAudiobookSourceFileSystem

class AppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(app)

        val sqlDriverManga = AndroidSqliteDriver(
            schema = Database.Schema,
            context = app,
            name = "tachiyomi.db",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        val sqlDriverAnime = AndroidSqliteDriver(
            schema = AnimeDatabase.Schema,
            context = app,
            name = "tachiyomi.animedb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(AnimeDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        val sqlDriverAudiobook = AndroidSqliteDriver(
            schema = AudiobookDatabase.Schema,
            context = app,
            name = "kikuyomi.audiobookdb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(AudiobookDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

        addSingletonFactory {
            Database(
                driver = sqlDriverManga,
                historyAdapter = History.Adapter(
                    last_readAdapter = DateColumnAdapter,
                ),
                mangasAdapter = Mangas.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                ),
            )
        }

        addSingletonFactory {
            AnimeDatabase(
                driver = sqlDriverAnime,
                animehistoryAdapter = Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                animesAdapter = Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                ),
            )
        }

        addSingletonFactory {
            AudiobookDatabase(
                driver = sqlDriverAudiobook,
                audiobookhistoryAdapter = Audiobookhistory.Adapter(
                    last_readAdapter = DateColumnAdapter
                ),
                audiobooksAdapter = Audiobooks.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AudiobookUpdateStrategyColumnAdapter
                ),
            )
        }




        addSingletonFactory<MangaDatabaseHandler> {
            AndroidMangaDatabaseHandler(
                get(),
                sqlDriverManga,
            )
        }

        addSingletonFactory<AnimeDatabaseHandler> {
            AndroidAnimeDatabaseHandler(
                get(),
                sqlDriverAnime,
            )
        }

        addSingletonFactory<AudiobookDatabaseHandler> {
            AndroidAudiobookDatabaseHandler(
                get(),
                sqlDriverAudiobook,
            )
        }

        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
        addSingletonFactory {
            XML {
                defaultPolicy {
                    ignoreUnknownChildren()
                }
                autoPolymorphic = true
                xmlDeclMode = XmlDeclMode.Charset
                indent = 2
                xmlVersion = XmlVersion.XML10
            }
        }

        addSingletonFactory { ChapterCache(app) }
        addSingletonFactory { EpisodeCache(app) }
        addSingletonFactory { AudiobookChapterCache(app) }

        addSingletonFactory { MangaCoverCache(app) }
        addSingletonFactory { AnimeCoverCache(app) }
        addSingletonFactory { AudiobookCoverCache(app) }

        addSingletonFactory { NetworkHelper(app, get()) }
        addSingletonFactory { JavaScriptEngine(app) }

        addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get(), get()) }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get()) }
        addSingletonFactory<AudiobookSourceManager> { AndroidAudiobookSourceManager(app, get(), get()) }

        addSingletonFactory { MangaExtensionManager(app) }
        addSingletonFactory { AnimeExtensionManager(app) }
        addSingletonFactory { AudiobookExtensionManager(app) }

        addSingletonFactory { MangaDownloadProvider(app) }
        addSingletonFactory { MangaDownloadManager(app) }
        addSingletonFactory { MangaDownloadCache(app) }

        addSingletonFactory { AnimeDownloadProvider(app) }
        addSingletonFactory { AnimeDownloadManager(app) }
        addSingletonFactory { AnimeDownloadCache(app) }

        addSingletonFactory { AudiobookDownloadProvider(app) }
        addSingletonFactory { AudiobookDownloadManager(app) }
        addSingletonFactory { AudiobookDownloadCache(app) }

        addSingletonFactory { TrackerManager(app) }
        addSingletonFactory { DelayedAnimeTrackingStore(app) }
        addSingletonFactory { DelayedMangaTrackingStore(app) }
        addSingletonFactory { DelayedAudiobookTrackingStore(app) }

        addSingletonFactory { ImageSaver(app) }

        addSingletonFactory { AndroidStorageFolderProvider(app) }

        addSingletonFactory { LocalMangaSourceFileSystem(get()) }
        addSingletonFactory { LocalMangaCoverManager(app, get()) }

        addSingletonFactory { LocalAnimeSourceFileSystem(get()) }
        addSingletonFactory { LocalAnimeCoverManager(app, get()) }

        addSingletonFactory { LocalAudiobookSourceFileSystem(get()) }
        addSingletonFactory { LocalAudiobookCoverManager(app, get()) }

        addSingletonFactory { StorageManager(app, get()) }

        addSingletonFactory { ExternalIntents() }

        // Asynchronously init expensive components for a faster cold start
        ContextCompat.getMainExecutor(app).execute {
            get<NetworkHelper>()

            get<MangaSourceManager>()
            get<AnimeSourceManager>()
            get<AudiobookSourceManager>()

            get<Database>()
            get<AnimeDatabase>()
            get<AudiobookDatabase>()

            get<MangaDownloadManager>()
            get<AnimeDownloadManager>()
            get<AudiobookDownloadManager>()
        }
    }
}

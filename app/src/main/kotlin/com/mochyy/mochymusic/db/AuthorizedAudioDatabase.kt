package com.mochyy.mochymusic.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mochyy.mochymusic.playback.audio.AudioSourceResult
import com.mochyy.mochymusic.playback.audio.AuthorizedSourceType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room Entity storing verified, legally authorized audio stream resolutions
 * (`LOCAL_DEVICE` or `LICENSED_CREATIVE_COMMONS`).
 *
 * SQLite triggers installed in [AuthorizedAudioDatabase.SQL_COMPLIANCE_RULES]
 * strictly forbid inserting or updating any YouTube/GoogleVideo stream URLs.
 */
@Entity(
    tableName = "authorized_audio_sources",
    indices = [
        Index(value = ["normalized_title", "normalized_artist"]),
        Index(value = ["source_type"]),
        Index(value = ["updated_at"]),
    ],
)
data class AuthorizedAudioCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,
    @ColumnInfo(name = "normalized_artist")
    val normalizedArtist: String,
    @ColumnInfo(name = "stream_uri")
    val streamUri: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "provider_label")
    val providerLabel: String,
    @ColumnInfo(name = "license_label")
    val licenseLabel: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int? = null,
    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toAuthorizedStream(): AudioSourceResult.AuthorizedStream {
        val parsedType = runCatching {
            AuthorizedSourceType.valueOf(sourceType)
        }.getOrDefault(AuthorizedSourceType.LICENSED_CREATIVE_COMMONS)

        return AudioSourceResult.AuthorizedStream(
            uri = streamUri,
            title = title,
            artist = artist,
            sourceType = parsedType,
            providerLabel = providerLabel,
            licenseLabel = licenseLabel,
            mimeType = mimeType,
            durationSeconds = durationSeconds,
            matchedTrackId = songId,
            artworkUrl = artworkUrl,
        )
    }
}

/**
 * Room Entity caching discovery catalogue metadata & lyrics retrieved from `sigma67/ytmusicapi`.
 * Contains strictly catalogue metadata, artwork URLs, and lyrics — never audio stream URLs.
 */
@Entity(
    tableName = "discovery_catalog_cache",
    indices = [
        Index(value = ["title", "artist"]),
        Index(value = ["cached_at"]),
    ],
)
data class DiscoveryCatalogCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "album_title")
    val albumTitle: String? = null,
    @ColumnInfo(name = "album_browse_id")
    val albumBrowseId: String? = null,
    @ColumnInfo(name = "artist_browse_id")
    val artistBrowseId: String? = null,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int? = null,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,
    @ColumnInfo(name = "lyrics_text")
    val lyricsText: String? = null,
    @ColumnInfo(name = "has_synced_lyrics")
    val hasSyncedLyrics: Boolean = false,
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis(),
)

@Dao
interface AuthorizedAudioDao {
    @Query("SELECT * FROM authorized_audio_sources ORDER BY updated_at DESC")
    fun observeAllAuthorizedSources(): Flow<List<AuthorizedAudioCacheEntity>>

    @Query("SELECT * FROM authorized_audio_sources WHERE song_id = :songId LIMIT 1")
    fun observeBySongId(songId: String): Flow<AuthorizedAudioCacheEntity?>

    @Query("SELECT * FROM authorized_audio_sources WHERE song_id = :songId LIMIT 1")
    suspend fun getBySongId(songId: String): AuthorizedAudioCacheEntity?

    @Query(
        """
        SELECT * FROM authorized_audio_sources
        WHERE normalized_title = :normalizedTitle
          AND (normalized_artist = :normalizedArtist OR :normalizedArtist = '')
        ORDER BY updated_at DESC
        LIMIT 1
        """
    )
    suspend fun findByNormalizedMetadata(
        normalizedTitle: String,
        normalizedArtist: String,
    ): AuthorizedAudioCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuthorizedSource(entity: AuthorizedAudioCacheEntity)

    @Query("DELETE FROM authorized_audio_sources WHERE song_id = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM authorized_audio_sources WHERE updated_at < :olderThanEpochMs")
    suspend fun pruneExpiredSources(olderThanEpochMs: Long)

    @Query("SELECT * FROM discovery_catalog_cache ORDER BY cached_at DESC")
    fun observeDiscoveryCatalog(): Flow<List<DiscoveryCatalogCacheEntity>>

    @Query("SELECT * FROM discovery_catalog_cache WHERE video_id = :videoId LIMIT 1")
    suspend fun getDiscoveryTrack(videoId: String): DiscoveryCatalogCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiscoveryTrack(entity: DiscoveryCatalogCacheEntity)
}

@Database(
    entities = [
        AuthorizedAudioCacheEntity::class,
        DiscoveryCatalogCacheEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AuthorizedAudioDatabase : RoomDatabase() {
    abstract fun authorizedAudioDao(): AuthorizedAudioDao

    companion object {
        /**
         * SQL integrity and compliance rules enforced via SQLite triggers:
         * 1. Rejects any empty `stream_uri` or unapproved `source_type`.
         * 2. Rejects any `stream_uri` pointing to `googlevideo.com`, `youtube.com/watch`,
         *    or `youtu.be`, guaranteeing at the database level that unauthorized YouTube
         *    audiovisual streams can never be stored.
         */
        val SQL_COMPLIANCE_RULES = listOf(
            """
            CREATE TRIGGER IF NOT EXISTS trg_validate_authorized_audio_insert
            BEFORE INSERT ON authorized_audio_sources
            FOR EACH ROW
            BEGIN
                SELECT CASE
                    WHEN NEW.stream_uri IS NULL OR TRIM(NEW.stream_uri) = ''
                        THEN RAISE(ABORT, 'SQL Rule Violation: stream_uri must not be empty')
                    WHEN NEW.source_type NOT IN ('LOCAL_DEVICE', 'LICENSED_CREATIVE_COMMONS')
                        THEN RAISE(ABORT, 'SQL Rule Violation: source_type must be LOCAL_DEVICE or LICENSED_CREATIVE_COMMONS')
                    WHEN LOWER(NEW.stream_uri) LIKE '%googlevideo.com%'
                      OR LOWER(NEW.stream_uri) LIKE '%youtube.com/watch%'
                      OR LOWER(NEW.stream_uri) LIKE '%youtu.be/%'
                      OR LOWER(NEW.stream_uri) LIKE '%music.youtube.com/watch%'
                        THEN RAISE(ABORT, 'SQL Compliance Violation: Unauthorized YouTube stream URLs are prohibited')
                END;
            END;
            """.trimIndent(),
            """
            CREATE TRIGGER IF NOT EXISTS trg_validate_authorized_audio_update
            BEFORE UPDATE ON authorized_audio_sources
            FOR EACH ROW
            BEGIN
                SELECT CASE
                    WHEN NEW.stream_uri IS NULL OR TRIM(NEW.stream_uri) = ''
                        THEN RAISE(ABORT, 'SQL Rule Violation: stream_uri must not be empty')
                    WHEN NEW.source_type NOT IN ('LOCAL_DEVICE', 'LICENSED_CREATIVE_COMMONS')
                        THEN RAISE(ABORT, 'SQL Rule Violation: source_type must be LOCAL_DEVICE or LICENSED_CREATIVE_COMMONS')
                    WHEN LOWER(NEW.stream_uri) LIKE '%googlevideo.com%'
                      OR LOWER(NEW.stream_uri) LIKE '%youtube.com/watch%'
                      OR LOWER(NEW.stream_uri) LIKE '%youtu.be/%'
                      OR LOWER(NEW.stream_uri) LIKE '%music.youtube.com/watch%'
                        THEN RAISE(ABORT, 'SQL Compliance Violation: Unauthorized YouTube stream URLs are prohibited')
                END;
            END;
            """.trimIndent(),
        )

        fun installSqlComplianceRules(db: SupportSQLiteDatabase) {
            SQL_COMPLIANCE_RULES.forEach { sql ->
                db.execSQL(sql)
            }
        }
    }
}

@Singleton
class AuthorizedAudioRepository @Inject constructor(
    private val dao: AuthorizedAudioDao,
) {
    val allAuthorizedSources: Flow<List<AuthorizedAudioCacheEntity>> =
        dao.observeAllAuthorizedSources()

    val discoveryCatalog: Flow<List<DiscoveryCatalogCacheEntity>> =
        dao.observeDiscoveryCatalog()

    fun observeBySongId(songId: String): Flow<AuthorizedAudioCacheEntity?> =
        dao.observeBySongId(songId)

    suspend fun getCachedStream(
        songId: String,
        normalizedTitle: String,
        normalizedArtist: String,
    ): AudioSourceResult.AuthorizedStream? {
        dao.getBySongId(songId)?.let { return it.toAuthorizedStream() }
        if (normalizedTitle.isNotBlank()) {
            dao.findByNormalizedMetadata(normalizedTitle, normalizedArtist)?.let {
                return it.toAuthorizedStream()
            }
        }
        return null
    }

    suspend fun saveAuthorizedStream(
        songId: String,
        normalizedTitle: String,
        normalizedArtist: String,
        stream: AudioSourceResult.AuthorizedStream,
    ) {
        val lowerUri = stream.uri.lowercase()
        if (
            lowerUri.contains("googlevideo.com") ||
            lowerUri.contains("youtube.com/watch") ||
            lowerUri.contains("youtu.be/") ||
            lowerUri.contains("music.youtube.com/watch")
        ) {
            throw IllegalArgumentException("SQL Compliance Violation: Unauthorized YouTube stream URLs are prohibited")
        }

        dao.upsertAuthorizedSource(
            AuthorizedAudioCacheEntity(
                songId = songId,
                title = stream.title,
                artist = stream.artist,
                normalizedTitle = normalizedTitle,
                normalizedArtist = normalizedArtist,
                streamUri = stream.uri,
                sourceType = stream.sourceType.name,
                providerLabel = stream.providerLabel,
                licenseLabel = stream.licenseLabel,
                mimeType = stream.mimeType,
                durationSeconds = stream.durationSeconds,
                artworkUrl = stream.artworkUrl,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun cacheDiscoveryMetadata(entity: DiscoveryCatalogCacheEntity) {
        dao.upsertDiscoveryTrack(entity)
    }

    suspend fun getDiscoveryMetadata(videoId: String): DiscoveryCatalogCacheEntity? =
        dao.getDiscoveryTrack(videoId)

    suspend fun deleteBySongId(songId: String) {
        dao.deleteBySongId(songId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AuthorizedAudioDatabaseModule {

    @Provides
    @Singleton
    fun provideAuthorizedAudioDatabase(
        @ApplicationContext context: Context,
    ): AuthorizedAudioDatabase {
        return Room.databaseBuilder(
            context,
            AuthorizedAudioDatabase::class.java,
            "asl_beats_authorized_audio.db",
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    runCatching {
                        AuthorizedAudioDatabase.installSqlComplianceRules(db)
                    }
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthorizedAudioDao(
        database: AuthorizedAudioDatabase,
    ): AuthorizedAudioDao = database.authorizedAudioDao()
}

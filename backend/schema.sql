-- =============================================================================
-- ASL Beats — Discovery Catalogue & Authorized Audio SQL Schema and Rules
-- =============================================================================
-- Enforces strict separation between:
--   1. Public YouTube Music Catalogue Metadata & Lyrics (sigma67/ytmusicapi)
--   2. Independently Authorized Audio Sources (LOCAL_DEVICE / LICENSED_CREATIVE_COMMONS)
-- =============================================================================

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

-- 1. Songs Catalogue Table (Metadata & Artwork only — NO audio stream URLs)
CREATE TABLE IF NOT EXISTS songs_catalog (
    video_id            TEXT PRIMARY KEY CHECK (LENGTH(TRIM(video_id)) > 0),
    title               TEXT NOT NULL CHECK (LENGTH(TRIM(title)) > 0),
    artist              TEXT NOT NULL DEFAULT 'Unknown Artist',
    artist_browse_id    TEXT,
    album_title         TEXT,
    album_browse_id     TEXT,
    duration_seconds    INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    thumbnail_url       TEXT,
    is_explicit         INTEGER NOT NULL DEFAULT 0 CHECK (is_explicit IN (0, 1)),
    lyrics_browse_id    TEXT,
    related_browse_id   TEXT,
    streaming_authorized INTEGER NOT NULL DEFAULT 0 CHECK (streaming_authorized = 0),
    updated_at          INTEGER NOT NULL DEFAULT (CAST(strftime('%s', 'now') AS INTEGER))
);

CREATE INDEX IF NOT EXISTS idx_songs_catalog_title_artist
    ON songs_catalog (LOWER(title), LOWER(artist));

-- 2. Lyrics Catalogue Table (Plain and Synchronized Timestamped Lyrics)
CREATE TABLE IF NOT EXISTS lyrics_catalog (
    video_id            TEXT PRIMARY KEY REFERENCES songs_catalog(video_id) ON DELETE CASCADE,
    available           INTEGER NOT NULL DEFAULT 0 CHECK (available IN (0, 1)),
    has_timestamps      INTEGER NOT NULL DEFAULT 0 CHECK (has_timestamps IN (0, 1)),
    lyrics_text         TEXT NOT NULL DEFAULT '',
    lines_json          TEXT NOT NULL DEFAULT '[]',
    source              TEXT,
    updated_at          INTEGER NOT NULL DEFAULT (CAST(strftime('%s', 'now') AS INTEGER))
);

-- 3. Playlists Catalogue Table
CREATE TABLE IF NOT EXISTS playlists_catalog (
    playlist_id         TEXT PRIMARY KEY CHECK (LENGTH(TRIM(playlist_id)) > 0),
    title               TEXT NOT NULL CHECK (LENGTH(TRIM(title)) > 0),
    author_name         TEXT,
    author_browse_id    TEXT,
    description         TEXT,
    track_count         INTEGER CHECK (track_count IS NULL OR track_count >= 0),
    thumbnail_url       TEXT,
    updated_at          INTEGER NOT NULL DEFAULT (CAST(strftime('%s', 'now') AS INTEGER))
);

CREATE TABLE IF NOT EXISTS playlist_tracks (
    playlist_id         TEXT NOT NULL REFERENCES playlists_catalog(playlist_id) ON DELETE CASCADE,
    position            INTEGER NOT NULL CHECK (position >= 0),
    video_id            TEXT NOT NULL REFERENCES songs_catalog(video_id) ON DELETE CASCADE,
    PRIMARY KEY (playlist_id, position)
);

-- 4. Authorized Audio Sources Table (Strictly Authorized Streams Only)
CREATE TABLE IF NOT EXISTS authorized_audio_sources (
    song_id             TEXT PRIMARY KEY CHECK (LENGTH(TRIM(song_id)) > 0),
    title               TEXT NOT NULL CHECK (LENGTH(TRIM(title)) > 0),
    artist              TEXT NOT NULL DEFAULT 'Unknown Artist',
    normalized_title    TEXT NOT NULL CHECK (LENGTH(TRIM(normalized_title)) > 0),
    normalized_artist   TEXT NOT NULL DEFAULT '',
    stream_uri          TEXT NOT NULL CHECK (LENGTH(TRIM(stream_uri)) > 0),
    source_type         TEXT NOT NULL CHECK (source_type IN ('LOCAL_DEVICE', 'LICENSED_CREATIVE_COMMONS')),
    provider_label      TEXT NOT NULL CHECK (LENGTH(TRIM(provider_label)) > 0),
    license_label       TEXT NOT NULL CHECK (LENGTH(TRIM(license_label)) > 0),
    mime_type           TEXT,
    duration_seconds    INTEGER CHECK (duration_seconds IS NULL OR duration_seconds > 0),
    updated_at          INTEGER NOT NULL DEFAULT (CAST(strftime('%s', 'now') AS INTEGER))
);

CREATE INDEX IF NOT EXISTS idx_authorized_audio_normalized
    ON authorized_audio_sources (normalized_title, normalized_artist);

-- =============================================================================
-- SQL Integrity & Compliance Triggers (Database Rules)
-- =============================================================================
-- Rule 1: Never allow YouTube or GoogleVideo stream URLs in authorized_audio_sources
CREATE TRIGGER IF NOT EXISTS trg_forbid_youtube_audio_stream_insert
BEFORE INSERT ON authorized_audio_sources
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN LOWER(NEW.stream_uri) LIKE '%googlevideo.com%'
          OR LOWER(NEW.stream_uri) LIKE '%youtube.com/watch%'
          OR LOWER(NEW.stream_uri) LIKE '%youtu.be/%'
          OR LOWER(NEW.stream_uri) LIKE '%music.youtube.com/watch%'
        THEN RAISE(ABORT, 'SQL Compliance Rule Violation: YouTube audio extraction or stream retention is strictly prohibited.')
    END;
END;

CREATE TRIGGER IF NOT EXISTS trg_forbid_youtube_audio_stream_update
BEFORE UPDATE ON authorized_audio_sources
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN LOWER(NEW.stream_uri) LIKE '%googlevideo.com%'
          OR LOWER(NEW.stream_uri) LIKE '%youtube.com/watch%'
          OR LOWER(NEW.stream_uri) LIKE '%youtu.be/%'
          OR LOWER(NEW.stream_uri) LIKE '%music.youtube.com/watch%'
        THEN RAISE(ABORT, 'SQL Compliance Rule Violation: YouTube audio extraction or stream retention is strictly prohibited.')
    END;
END;

-- Rule 2: Synchronized lyrics flag requires non-empty lines_json when has_timestamps = 1
CREATE TRIGGER IF NOT EXISTS trg_validate_synced_lyrics_insert
BEFORE INSERT ON lyrics_catalog
FOR EACH ROW
BEGIN
    SELECT CASE
        WHEN NEW.has_timestamps = 1 AND (NEW.lines_json IS NULL OR TRIM(NEW.lines_json) IN ('', '[]'))
        THEN RAISE(ABORT, 'SQL Rule Violation: has_timestamps=1 requires timestamped lyric lines.')
    END;
END;

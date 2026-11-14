from __future__ import annotations

import json
import os
import sqlite3
import time
from pathlib import Path
from typing import Any, Dict, Optional

from models import LyricsResponse, SongDetailResponse, TrackSummary

SCHEMA_PATH = Path(__file__).with_name("schema.sql")
DEFAULT_DB_PATH = os.environ.get("ASL_BEATS_DB_PATH", str(Path(__file__).with_name("asl_beats_discovery.db")))


class DiscoverySqlDatabase:
    """
    SQLite persistence & compliance rules layer for the ASL Beats FastAPI Discovery Backend.
    Caches public catalogue metadata & lyrics while enforcing SQL triggers that forbid
    storing unauthorized YouTube audio stream URLs.
    """

    def __init__(self, db_path: str = DEFAULT_DB_PATH) -> None:
        self.db_path = db_path
        self._init_schema()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON;")
        return conn

    def _init_schema(self) -> None:
        schema_sql = SCHEMA_PATH.read_text(encoding="utf-8")
        with self._connect() as conn:
            conn.executescript(schema_sql)
            conn.commit()

    def upsert_track_summary(self, track: TrackSummary) -> None:
        artist_id = track.artists[0].id if track.artists else None
        album_title = track.album.name if track.album else None
        album_id = track.album.id if track.album else None
        now = int(time.time())
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO songs_catalog (
                    video_id, title, artist, artist_browse_id, album_title, album_browse_id,
                    duration_seconds, thumbnail_url, is_explicit, streaming_authorized, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                ON CONFLICT(video_id) DO UPDATE SET
                    title = excluded.title,
                    artist = excluded.artist,
                    artist_browse_id = COALESCE(excluded.artist_browse_id, songs_catalog.artist_browse_id),
                    album_title = COALESCE(excluded.album_title, songs_catalog.album_title),
                    album_browse_id = COALESCE(excluded.album_browse_id, songs_catalog.album_browse_id),
                    duration_seconds = COALESCE(excluded.duration_seconds, songs_catalog.duration_seconds),
                    thumbnail_url = COALESCE(excluded.thumbnail_url, songs_catalog.thumbnail_url),
                    is_explicit = excluded.is_explicit,
                    updated_at = excluded.updated_at
                """,
                (
                    track.videoId,
                    track.title,
                    track.artist,
                    artist_id,
                    album_title,
                    album_id,
                    track.durationSeconds,
                    track.thumbnailUrl,
                    1 if track.isExplicit else 0,
                    now,
                ),
            )
            conn.commit()

    def upsert_song_detail(self, detail: SongDetailResponse) -> None:
        artist_id = detail.artists[0].id if detail.artists else None
        album_title = detail.album.name if detail.album else None
        album_id = detail.album.id if detail.album else None
        now = int(time.time())
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO songs_catalog (
                    video_id, title, artist, artist_browse_id, album_title, album_browse_id,
                    duration_seconds, thumbnail_url, is_explicit, lyrics_browse_id,
                    related_browse_id, streaming_authorized, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                ON CONFLICT(video_id) DO UPDATE SET
                    title = excluded.title,
                    artist = excluded.artist,
                    artist_browse_id = COALESCE(excluded.artist_browse_id, songs_catalog.artist_browse_id),
                    album_title = COALESCE(excluded.album_title, songs_catalog.album_title),
                    album_browse_id = COALESCE(excluded.album_browse_id, songs_catalog.album_browse_id),
                    duration_seconds = COALESCE(excluded.duration_seconds, songs_catalog.duration_seconds),
                    thumbnail_url = COALESCE(excluded.thumbnail_url, songs_catalog.thumbnail_url),
                    is_explicit = excluded.is_explicit,
                    lyrics_browse_id = COALESCE(excluded.lyrics_browse_id, songs_catalog.lyrics_browse_id),
                    related_browse_id = COALESCE(excluded.related_browse_id, songs_catalog.related_browse_id),
                    updated_at = excluded.updated_at
                """,
                (
                    detail.videoId,
                    detail.title,
                    detail.artist,
                    artist_id,
                    album_title,
                    album_id,
                    detail.durationSeconds,
                    detail.thumbnailUrl,
                    1 if detail.isExplicit else 0,
                    detail.lyricsBrowseId,
                    detail.relatedBrowseId,
                    now,
                ),
            )
            conn.commit()

    def upsert_lyrics(self, lyrics: LyricsResponse) -> None:
        now = int(time.time())
        lines_payload = [
            {
                "startTimeMs": line.startTimeMs,
                "endTimeMs": line.endTimeMs,
                "text": line.text,
            }
            for line in lyrics.lines
        ]
        has_ts = 1 if (lyrics.hasTimestamps and lines_payload) else 0
        with self._connect() as conn:
            # Ensure parent song row exists for FK integrity
            conn.execute(
                """
                INSERT OR IGNORE INTO songs_catalog (video_id, title, artist, updated_at)
                VALUES (?, ?, 'Unknown Artist', ?)
                """,
                (lyrics.videoId, lyrics.videoId, now),
            )
            conn.execute(
                """
                INSERT INTO lyrics_catalog (
                    video_id, available, has_timestamps, lyrics_text, lines_json, source, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(video_id) DO UPDATE SET
                    available = excluded.available,
                    has_timestamps = excluded.has_timestamps,
                    lyrics_text = excluded.lyrics_text,
                    lines_json = excluded.lines_json,
                    source = excluded.source,
                    updated_at = excluded.updated_at
                """,
                (
                    lyrics.videoId,
                    1 if lyrics.available else 0,
                    has_ts,
                    lyrics.lyricsText,
                    json.dumps(lines_payload),
                    lyrics.source,
                    now,
                ),
            )
            conn.commit()

    def register_authorized_audio_source(
        self,
        song_id: str,
        title: str,
        artist: str,
        normalized_title: str,
        normalized_artist: str,
        stream_uri: str,
        source_type: str,
        provider_label: str,
        license_label: str,
        mime_type: Optional[str] = None,
        duration_seconds: Optional[int] = None,
    ) -> None:
        now = int(time.time())
        with self._connect() as conn:
            conn.execute(
                """
                INSERT INTO authorized_audio_sources (
                    song_id, title, artist, normalized_title, normalized_artist,
                    stream_uri, source_type, provider_label, license_label,
                    mime_type, duration_seconds, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(song_id) DO UPDATE SET
                    stream_uri = excluded.stream_uri,
                    source_type = excluded.source_type,
                    provider_label = excluded.provider_label,
                    license_label = excluded.license_label,
                    updated_at = excluded.updated_at
                """,
                (
                    song_id,
                    title,
                    artist,
                    normalized_title,
                    normalized_artist,
                    stream_uri,
                    source_type,
                    provider_label,
                    license_label,
                    mime_type,
                    duration_seconds,
                    now,
                ),
            )
            conn.commit()

    def get_stats(self) -> Dict[str, Any]:
        with self._connect() as conn:
            songs_count = conn.execute("SELECT COUNT(*) FROM songs_catalog").fetchone()[0]
            lyrics_count = conn.execute("SELECT COUNT(*) FROM lyrics_catalog WHERE available = 1").fetchone()[0]
            auth_count = conn.execute("SELECT COUNT(*) FROM authorized_audio_sources").fetchone()[0]
        return {
            "cachedSongs": songs_count,
            "cachedLyrics": lyrics_count,
            "authorizedAudioMappings": auth_count,
        }

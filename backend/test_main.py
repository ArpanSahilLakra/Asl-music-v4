from __future__ import annotations

import os
import sys
import time
import unittest
from typing import Any, Dict, List, Optional

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from normalizer import (
    normalize_album_detail,
    normalize_artist_detail,
    normalize_lyrics,
    normalize_playlist_detail,
    normalize_radio,
    normalize_search_results,
    normalize_song_detail,
)

try:
    from fastapi.testclient import TestClient
    from main import app, get_ytmusic_client
    HAS_FASTAPI = True
except ImportError:
    HAS_FASTAPI = False


class FakeYTMusic:
    def get_search_suggestions(self, query: str) -> List[str]:
        if query == "rate_limit":
            raise RuntimeError("429 Too Many Requests")
        return [f"{query} official", f"{query} remix"]

    def search(self, query: str, filter: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
        if query == "rate_limit":
            raise RuntimeError("HTTP 429 rate limit exceeded")
        if query == "timeout_query":
            time.sleep(0.25)
            return []
        return [
            {
                "resultType": "song",
                "videoId": "vid_001",
                "title": "Midnight Horizon",
                "artists": [{"name": "Aurora Pulse", "id": "UC_ARTIST_1"}],
                "album": {"name": "Neon Echoes", "id": "MPREb_ALBUM_1"},
                "duration": "3:45",
                "duration_seconds": 225,
                "thumbnails": [
                    {"url": "https://img.example/low.jpg", "width": 120, "height": 120},
                    {"url": "https://img.example/high.jpg", "width": 544, "height": 544},
                ],
                "isExplicit": False,
            },
            {
                "resultType": "song",
                "videoId": "vid_no_thumb",
                "title": "Silent Skylines",
                "artists": [{"name": "Aurora Pulse", "id": "UC_ARTIST_1"}],
                "duration": "2:10",
                "thumbnails": [],
            },
            {
                "resultType": "song",
                "videoId": "vid_private",
                "title": "Private video",
                "isAvailable": False,
            },
            {
                "resultType": "album",
                "browseId": "MPREb_ALBUM_1",
                "audioPlaylistId": "OLAK5uy_1",
                "title": "Neon Echoes",
                "artists": [{"name": "Aurora Pulse", "id": "UC_ARTIST_1"}],
                "year": "2025",
                "thumbnails": [{"url": "https://img.example/album.jpg", "width": 512, "height": 512}],
            },
        ]

    def get_song(self, video_id: str) -> Dict[str, Any]:
        if video_id == "private_id":
            return {"playabilityStatus": {"status": "LOGIN_REQUIRED"}, "videoDetails": {}}
        if video_id == "not_found_id":
            raise RuntimeError("404 Not Found")
        return {
            "playabilityStatus": {"status": "OK"},
            "videoDetails": {
                "videoId": video_id,
                "title": "Midnight Horizon",
                "author": "Aurora Pulse",
                "lengthSeconds": "225",
                "thumbnail": {
                    "thumbnails": [
                        {"url": "https://img.example/song_hd.jpg", "width": 800, "height": 800}
                    ]
                },
            },
        }

    def get_watch_playlist(self, videoId: Optional[str] = None, limit: int = 25) -> Dict[str, Any]:
        if videoId == "no_lyrics_id":
            return {"tracks": [], "lyrics": None}
        if videoId == "synced_lyrics_id":
            return {"tracks": [], "lyrics": "MPLYt_SYNCED"}
        return {
            "playlistId": "RDAMVM_vid_001",
            "lyrics": "MPLYt_PLAIN",
            "related": "MPTRt_REL_1",
            "tracks": [
                {
                    "videoId": videoId or "vid_001",
                    "title": "Midnight Horizon",
                    "artists": [{"name": "Aurora Pulse", "id": "UC_ARTIST_1"}],
                    "album": {"name": "Neon Echoes", "id": "MPREb_ALBUM_1"},
                    "length": "3:45",
                },
                {
                    "videoId": "vid_rec_2",
                    "title": "Starlight Drift",
                    "artists": [{"name": "Solar Wind", "id": "UC_ARTIST_2"}],
                    "length": "4:02",
                    "thumbnail": [{"url": "https://img.example/rec2.jpg", "width": 400, "height": 400}],
                },
            ],
        }

    def get_lyrics(self, browse_id: str) -> Dict[str, Any]:
        if browse_id == "MPLYt_SYNCED":
            return {
                "hasTimestamps": True,
                "lyrics": [
                    {"text": "Walking through the neon rain", "start_time": 12000, "end_time": 16500},
                    {"text": "Chasing echoes in the dark", "start_time": 16500, "end_time": 21000},
                ],
                "source": "Musixmatch",
            }
        return {
            "hasTimestamps": False,
            "lyrics": "Walking through the neon rain\nChasing echoes in the dark",
            "source": "YouTube Music",
        }

    def get_album(self, browse_id: str) -> Dict[str, Any]:
        if browse_id == "missing_album":
            raise RuntimeError("Unable to find album 404")
        return {
            "title": "Neon Echoes",
            "audioPlaylistId": "OLAK5uy_1",
            "year": "2025",
            "artists": [{"name": "Aurora Pulse", "id": "UC_ARTIST_1"}],
            "thumbnails": [{"url": "https://img.example/album.jpg", "width": 512, "height": 512}],
            "trackCount": 2,
            "duration_seconds": 450,
            "tracks": [
                {"videoId": "vid_001", "title": "Midnight Horizon", "artists": [{"name": "Aurora Pulse"}], "duration_seconds": 225},
                {"videoId": "vid_002", "title": "Dawn Patrol", "artists": [{"name": "Aurora Pulse"}], "duration_seconds": 225},
            ],
        }

    def get_artist(self, browse_id: str) -> Dict[str, Any]:
        if browse_id == "missing_artist":
            raise RuntimeError("404 artist not found")
        return {
            "name": "Aurora Pulse",
            "description": "Electronic synthwave duo.",
            "subscribers": "120K",
            "thumbnails": [{"url": "https://img.example/artist.jpg", "width": 600, "height": 600}],
            "shuffleId": "RDA_SHUFFLE",
            "radioId": "RDA_RADIO",
            "songs": {
                "results": [
                    {"videoId": "vid_001", "title": "Midnight Horizon", "artists": [{"name": "Aurora Pulse"}], "duration": "3:45"}
                ]
            },
            "albums": {
                "results": [
                    {"browseId": "MPREb_ALBUM_1", "title": "Neon Echoes", "year": "2025"}
                ]
            },
        }

    def get_playlist(self, playlist_id: str, limit: int = 100) -> Dict[str, Any]:
        if playlist_id == "private_pl":
            raise RuntimeError("404 playlist not found or private")
        tracks = [
            {
                "videoId": f"pl_vid_{idx}",
                "title": f"Playlist Track {idx}",
                "artists": [{"name": "Artist X", "id": "UC_X"}],
                "duration": "3:00",
                "duration_seconds": 180,
            }
            for idx in range(1, 6)
        ]
        return {
            "id": playlist_id,
            "title": "Synthwave Essentials",
            "author": {"name": "ASL Curator", "id": "UC_CURATOR"},
            "trackCount": 5,
            "thumbnails": [{"url": "https://img.example/pl.jpg", "width": 500, "height": 500}],
            "tracks": tracks,
        }


class YtMusicBackendNormalizerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.yt = FakeYTMusic()

    def test_search_and_missing_thumbnail_fallback(self) -> None:
        raw = self.yt.search("Midnight")
        suggestions = self.yt.get_search_suggestions("Midnight")
        resp = normalize_search_results("Midnight", raw, suggestions=suggestions)
        self.assertEqual(resp.query, "Midnight")
        self.assertEqual(len(resp.items), 2)
        self.assertEqual(resp.items[0].videoId, "vid_001")
        self.assertEqual(resp.items[0].thumbnailUrl, "https://img.example/high.jpg")
        self.assertEqual(resp.items[0].durationSeconds, 225)
        self.assertEqual(resp.items[1].videoId, "vid_no_thumb")
        self.assertEqual(resp.items[1].thumbnailUrl, "https://i.ytimg.com/vi/vid_no_thumb/hqdefault.jpg")
        self.assertEqual(len(resp.albums), 1)
        self.assertEqual(len(resp.suggestions), 2)

    def test_song_metadata_and_private_song(self) -> None:
        song_raw = self.yt.get_song("vid_001")
        watch_raw = self.yt.get_watch_playlist("vid_001")
        detail = normalize_song_detail("vid_001", song_raw, watch_raw)
        self.assertIsNotNone(detail)
        assert detail is not None
        self.assertEqual(detail.videoId, "vid_001")
        self.assertEqual(detail.title, "Midnight Horizon")
        self.assertEqual(detail.artist, "Aurora Pulse")
        self.assertFalse(detail.streamingAuthorized)
        self.assertEqual(len(detail.recommendedTracks), 1)

        priv_detail = normalize_song_detail("private_id", self.yt.get_song("private_id"), {})
        self.assertIsNone(priv_detail)

    def test_lyrics_plain_synced_and_missing(self) -> None:
        plain = normalize_lyrics("vid_001", self.yt.get_lyrics("MPLYt_PLAIN"))
        self.assertTrue(plain.available)
        self.assertFalse(plain.hasTimestamps)
        self.assertEqual(len(plain.lines), 2)

        synced = normalize_lyrics("synced_lyrics_id", self.yt.get_lyrics("MPLYt_SYNCED"))
        self.assertTrue(synced.available)
        self.assertTrue(synced.hasTimestamps)
        self.assertEqual(synced.lines[0].startTimeMs, 12000)
        self.assertIn("[00:12.00]", synced.lyricsText)

        missing = normalize_lyrics("no_lyrics_id", None)
        self.assertFalse(missing.available)

    def test_album_artist_playlist_pagination_and_radio(self) -> None:
        album = normalize_album_detail("MPREb_ALBUM_1", self.yt.get_album("MPREb_ALBUM_1"))
        self.assertIsNotNone(album)
        assert album is not None
        self.assertEqual(len(album.tracks), 2)

        artist = normalize_artist_detail("UC_ARTIST_1", self.yt.get_artist("UC_ARTIST_1"))
        self.assertIsNotNone(artist)
        assert artist is not None
        self.assertEqual(artist.name, "Aurora Pulse")

        raw_pl = self.yt.get_playlist("PL_SYNTH_1")
        p1 = normalize_playlist_detail("VLPL_SYNTH_1", raw_pl, offset=0, limit=2)
        assert p1 is not None
        self.assertEqual(p1.playlistId, "PL_SYNTH_1")
        self.assertEqual(len(p1.tracks), 2)
        self.assertEqual(p1.continuation, "2")

        p2 = normalize_playlist_detail("PL_SYNTH_1", raw_pl, offset=2, limit=2)
        assert p2 is not None
        self.assertEqual(len(p2.tracks), 2)
        self.assertEqual(p2.continuation, "4")

        radio = normalize_radio("vid_001", self.yt.get_watch_playlist("vid_001"))
        self.assertEqual(len(radio.tracks), 2)

    def test_sql_database_rules_and_compliance_triggers(self) -> None:
        import sqlite3
        import tempfile
        from database import DiscoverySqlDatabase

        with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
            db = DiscoverySqlDatabase(db_path=tmp.name)
            detail = normalize_song_detail("vid_001", self.yt.get_song("vid_001"), self.yt.get_watch_playlist("vid_001"))
            assert detail is not None
            db.upsert_song_detail(detail)

            synced = normalize_lyrics("vid_001", self.yt.get_lyrics("MPLYt_SYNCED"))
            db.upsert_lyrics(synced)

            # Valid licensed Creative Commons / Audius stream succeeds
            db.register_authorized_audio_source(
                song_id="vid_001",
                title="Midnight Horizon",
                artist="Aurora Pulse",
                normalized_title="midnight horizon",
                normalized_artist="aurora pulse",
                stream_uri="https://api.audius.co/v1/tracks/101/stream?app_name=asl_beats_android",
                source_type="LICENSED_CREATIVE_COMMONS",
                provider_label="Audius Authorized Catalog",
                license_label="Creative Commons BY-SA",
                mime_type="audio/mpeg",
                duration_seconds=225,
            )

            stats = db.get_stats()
            self.assertEqual(stats["cachedSongs"], 1)
            self.assertEqual(stats["cachedLyrics"], 1)
            self.assertEqual(stats["authorizedAudioMappings"], 1)

            # SQL Trigger Rule 1: Attempting to store a YouTube or GoogleVideo stream URL MUST raise IntegrityError
            with self.assertRaises(sqlite3.IntegrityError):
                db.register_authorized_audio_source(
                    song_id="vid_illegal",
                    title="Illegal Stream",
                    artist="Uploader",
                    normalized_title="illegal stream",
                    normalized_artist="uploader",
                    stream_uri="https://rr1---sn-abc.googlevideo.com/videoplayback?id=123",
                    source_type="LICENSED_CREATIVE_COMMONS",
                    provider_label="Invalid",
                    license_label="Invalid",
                )

            # SQL Check Rule 2: Invalid source_type MUST raise IntegrityError
            with self.assertRaises(sqlite3.IntegrityError):
                db.register_authorized_audio_source(
                    song_id="vid_invalid_type",
                    title="Invalid Type",
                    artist="Artist",
                    normalized_title="invalid type",
                    normalized_artist="artist",
                    stream_uri="content://media/external/audio/media/42",
                    source_type="YOUTUBE_EXTRACTOR",
                    provider_label="Invalid",
                    license_label="Invalid",
                )


if __name__ == "__main__":
    unittest.main()

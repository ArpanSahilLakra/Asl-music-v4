from __future__ import annotations

import asyncio
from functools import partial
from typing import Any, Callable, Optional

from fastapi import Depends, FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from ytmusicapi import YTMusic

from database import DiscoverySqlDatabase
from models import (
    AlbumDetailResponse,
    ArtistDetailResponse,
    LyricsResponse,
    PlaylistDetailResponse,
    RadioResponse,
    SearchResponse,
    SongDetailResponse,
    SuggestionsResponse,
)
from normalizer import (
    normalize_album_detail,
    normalize_artist_detail,
    normalize_lyrics,
    normalize_playlist_detail,
    normalize_radio,
    normalize_search_results,
    normalize_song_detail,
)

REQUEST_TIMEOUT_SECONDS = 12.0

app = FastAPI(
    title="ASL Beats — ytmusicapi Discovery Service",
    description=(
        "Unauthenticated YouTube Music metadata, artwork, and lyrics discovery "
        "powered by sigma67/ytmusicapi. Strictly metadata-only: never extracts audio streams."
    ),
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_default_ytmusic: Optional[YTMusic] = None
_default_db: Optional[DiscoverySqlDatabase] = None


def get_ytmusic_client() -> Any:
    global _default_ytmusic
    if _default_ytmusic is None:
        # Unauthenticated public catalogue mode — no cookies or OAuth required
        _default_ytmusic = YTMusic()
    return _default_ytmusic


def get_sql_db() -> DiscoverySqlDatabase:
    global _default_db
    if _default_db is None:
        _default_db = DiscoverySqlDatabase()
    return _default_db


async def call_ytmusic(func: Callable[..., Any], *args: Any, **kwargs: Any) -> Any:
    loop = asyncio.get_running_loop()
    bound = partial(func, *args, **kwargs)
    try:
        return await asyncio.wait_for(
            loop.run_in_executor(None, bound),
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
    except asyncio.TimeoutError as exc:
        raise HTTPException(
            status_code=504,
            detail="Upstream YouTube Music catalogue request timed out.",
        ) from exc
    except HTTPException:
        raise
    except Exception as exc:
        msg = str(exc).lower()
        if "429" in msg or "too many requests" in msg or "rate limit" in msg or "quota" in msg:
            raise HTTPException(
                status_code=429,
                detail="Rate limit reached on upstream YouTube Music catalogue.",
            ) from exc
        if "404" in msg or "not found" in msg or "unable to find" in msg or "invalid" in msg:
            raise HTTPException(
                status_code=404,
                detail="Requested music catalogue resource was not found or is private.",
            ) from exc
        raise HTTPException(
            status_code=502,
            detail=f"Upstream ytmusicapi catalogue error: {exc}",
        ) from exc


@app.get("/api/health")
async def health(db: DiscoverySqlDatabase = Depends(get_sql_db)) -> dict:
    return {
        "status": "ok",
        "service": "asl-beats-ytmusicapi-discovery",
        "mode": "public-unauthenticated",
        "audioStreamingAuthorized": False,
        "database": db.get_stats(),
    }


@app.get("/api/suggestions", response_model=SuggestionsResponse)
async def get_suggestions(
    q: str = Query("", description="Partial search query"),
    yt: Any = Depends(get_ytmusic_client),
) -> SuggestionsResponse:
    cleaned = q.strip()
    if not cleaned:
        return SuggestionsResponse(query="", suggestions=[])
    try:
        raw = await call_ytmusic(yt.get_search_suggestions, cleaned)
        suggestions = [str(s).strip() for s in (raw or []) if isinstance(s, str) and str(s).strip()]
    except HTTPException:
        suggestions = []
    return SuggestionsResponse(query=cleaned, suggestions=suggestions)


@app.get("/api/search", response_model=SearchResponse)
async def search_catalogue(
    q: str = Query(..., min_length=1, description="Search query string"),
    filter: Optional[str] = Query(None, description="Optional filter: songs, videos, albums, artists, playlists"),
    limit: int = Query(20, ge=1, le=50),
    offset: int = Query(0, ge=0),
    yt: Any = Depends(get_ytmusic_client),
    db: DiscoverySqlDatabase = Depends(get_sql_db),
) -> SearchResponse:
    cleaned = q.strip()
    if not cleaned:
        raise HTTPException(status_code=400, detail="Search query 'q' must not be empty.")

    allowed_filters = {"songs", "videos", "albums", "artists", "playlists", "community_playlists", "featured_playlists"}
    resolved_filter = filter.strip().lower() if filter and filter.strip().lower() in allowed_filters else None

    fetch_limit = min(offset + limit, 50)
    raw_results = await call_ytmusic(
        yt.search,
        cleaned,
        filter=resolved_filter,
        limit=fetch_limit,
    )
    sliced = (raw_results or [])[offset : offset + limit] if offset > 0 else (raw_results or [])

    suggestions = []
    if offset == 0:
        try:
            raw_sugg = await call_ytmusic(yt.get_search_suggestions, cleaned)
            suggestions = [str(s).strip() for s in (raw_sugg or []) if isinstance(s, str) and str(s).strip()][:8]
        except Exception:
            suggestions = []

    response = normalize_search_results(
        query=cleaned,
        raw_results=sliced,
        filter_name=resolved_filter,
        suggestions=suggestions,
        offset=offset,
        limit=limit,
    )
    for track in response.items:
        try:
            db.upsert_track_summary(track)
        except Exception:
            pass
    return response


@app.get("/api/song/{video_id}", response_model=SongDetailResponse)
async def get_song_metadata(
    video_id: str,
    yt: Any = Depends(get_ytmusic_client),
    db: DiscoverySqlDatabase = Depends(get_sql_db),
) -> SongDetailResponse:
    clean_id = video_id.strip()
    if not clean_id:
        raise HTTPException(status_code=400, detail="video_id is required.")

    raw_song = await call_ytmusic(yt.get_song, clean_id)
    watch_playlist = None
    try:
        watch_playlist = await call_ytmusic(yt.get_watch_playlist, videoId=clean_id, limit=15)
    except Exception:
        watch_playlist = None

    normalized = normalize_song_detail(clean_id, raw_song or {}, watch_playlist)
    if normalized is None:
        raise HTTPException(
            status_code=404,
            detail=f"Song '{clean_id}' is unavailable, private, or missing metadata.",
        )
    try:
        db.upsert_song_detail(normalized)
    except Exception:
        pass
    return normalized


@app.get("/api/lyrics/{video_id}", response_model=LyricsResponse)
async def get_song_lyrics(
    video_id: str,
    yt: Any = Depends(get_ytmusic_client),
    db: DiscoverySqlDatabase = Depends(get_sql_db),
) -> LyricsResponse:
    clean_id = video_id.strip()
    if not clean_id:
        raise HTTPException(status_code=400, detail="video_id is required.")

    try:
        watch = await call_ytmusic(yt.get_watch_playlist, videoId=clean_id, limit=5)
    except HTTPException as exc:
        if exc.status_code == 404:
            return LyricsResponse(videoId=clean_id, available=False)
        raise

    lyrics_browse_id = (watch or {}).get("lyrics") if isinstance(watch, dict) else None
    if not lyrics_browse_id:
        return LyricsResponse(videoId=clean_id, available=False)

    try:
        raw_lyrics = await call_ytmusic(yt.get_lyrics, str(lyrics_browse_id))
    except HTTPException as exc:
        if exc.status_code == 404:
            return LyricsResponse(videoId=clean_id, available=False)
        raise

    normalized = normalize_lyrics(clean_id, raw_lyrics)
    try:
        db.upsert_lyrics(normalized)
    except Exception:
        pass
    return normalized


@app.get("/api/album/{browse_id}", response_model=AlbumDetailResponse)
async def get_album_metadata(
    browse_id: str,
    yt: Any = Depends(get_ytmusic_client),
) -> AlbumDetailResponse:
    clean_id = browse_id.strip()
    if not clean_id:
        raise HTTPException(status_code=400, detail="browse_id is required.")

    raw_album = await call_ytmusic(yt.get_album, clean_id)
    normalized = normalize_album_detail(clean_id, raw_album or {})
    if normalized is None:
        raise HTTPException(status_code=404, detail=f"Album '{clean_id}' not found.")
    return normalized


@app.get("/api/artist/{browse_id}", response_model=ArtistDetailResponse)
async def get_artist_metadata(
    browse_id: str,
    yt: Any = Depends(get_ytmusic_client),
) -> ArtistDetailResponse:
    clean_id = browse_id.strip()
    if not clean_id:
        raise HTTPException(status_code=400, detail="browse_id is required.")

    raw_artist = await call_ytmusic(yt.get_artist, clean_id)
    normalized = normalize_artist_detail(clean_id, raw_artist or {})
    if normalized is None:
        raise HTTPException(status_code=404, detail=f"Artist '{clean_id}' not found.")
    return normalized


@app.get("/api/playlist/{playlist_id}", response_model=PlaylistDetailResponse)
async def get_playlist_metadata(
    playlist_id: str,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    yt: Any = Depends(get_ytmusic_client),
) -> PlaylistDetailResponse:
    clean_id = playlist_id.strip()
    if clean_id.startswith("VL") and len(clean_id) > 4:
        clean_id = clean_id[2:]
    if not clean_id:
        raise HTTPException(status_code=400, detail="playlist_id is required.")

    raw_playlist = await call_ytmusic(yt.get_playlist, clean_id, limit=offset + limit)
    normalized = normalize_playlist_detail(clean_id, raw_playlist or {}, offset=offset, limit=limit)
    if normalized is None:
        raise HTTPException(
            status_code=404,
            detail=f"Playlist '{clean_id}' was not found or is private.",
        )
    return normalized


@app.get("/api/radio/{video_id}", response_model=RadioResponse)
async def get_radio_recommendations(
    video_id: str,
    limit: int = Query(25, ge=1, le=50),
    yt: Any = Depends(get_ytmusic_client),
) -> RadioResponse:
    clean_id = video_id.strip()
    if not clean_id:
        raise HTTPException(status_code=400, detail="video_id is required.")

    raw_watch = await call_ytmusic(yt.get_watch_playlist, videoId=clean_id, limit=limit)
    normalized = normalize_radio(clean_id, raw_watch or {})
    if not normalized.tracks:
        raise HTTPException(
            status_code=404,
            detail=f"No radio recommendations available for '{clean_id}'.",
        )
    return normalized

from __future__ import annotations

import re
from typing import Any, Dict, List, Optional

from models import (
    AlbumDetailResponse,
    AlbumRef,
    AlbumSummary,
    ArtistDetailResponse,
    ArtistRef,
    ArtistSummary,
    LyricLine,
    LyricsResponse,
    PlaylistDetailResponse,
    PlaylistSummary,
    RadioResponse,
    SearchResponse,
    SongDetailResponse,
    TrackSummary,
)

LRC_TIMESTAMP_RE = re.compile(r"\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)")


def parse_duration_seconds(duration_val: Any) -> Optional[int]:
    if duration_val is None:
        return None
    if isinstance(duration_val, (int, float)):
        val = int(duration_val)
        return val if val > 0 else None
    text = str(duration_val).strip()
    if not text:
        return None
    if text.isdigit():
        val = int(text)
        return val if val > 0 else None
    parts = text.split(":")
    try:
        nums = [int(p) for p in parts]
    except ValueError:
        return None
    if len(nums) == 2:
        total = nums[0] * 60 + nums[1]
        return total if total > 0 else None
    if len(nums) == 3:
        total = nums[0] * 3600 + nums[1] * 60 + nums[2]
        return total if total > 0 else None
    return None


def format_duration_text(seconds: Optional[int]) -> Optional[str]:
    if not seconds or seconds <= 0:
        return None
    hours = seconds // 3600
    minutes = (seconds % 3600) // 60
    secs = seconds % 60
    if hours > 0:
        return f"{hours}:{minutes:02d}:{secs:02d}"
    return f"{minutes}:{secs:02d}"


def pick_best_thumbnail(raw: Any) -> Optional[str]:
    if not raw:
        return None
    if isinstance(raw, dict):
        if "thumbnails" in raw:
            return pick_best_thumbnail(raw.get("thumbnails"))
        url = raw.get("url")
        return str(url).strip() if url else None
    if isinstance(raw, list):
        best_url: Optional[str] = None
        best_area = -1
        for entry in raw:
            if not isinstance(entry, dict):
                continue
            url = entry.get("url")
            if not url or not str(url).strip():
                continue
            w = int(entry.get("width") or 0)
            h = int(entry.get("height") or 0)
            area = w * h
            if area >= best_area:
                best_area = area
                best_url = str(url).strip()
        return best_url
    return None


def parse_artists(raw_artists: Any, fallback_author: Optional[str] = None) -> List[ArtistRef]:
    refs: List[ArtistRef] = []
    if isinstance(raw_artists, list):
        for item in raw_artists:
            if isinstance(item, dict):
                name = str(item.get("name") or "").strip()
                if not name:
                    continue
                artist_id = item.get("id") or item.get("browseId")
                refs.append(ArtistRef(id=str(artist_id) if artist_id else None, name=name))
            elif isinstance(item, str) and item.strip():
                refs.append(ArtistRef(id=None, name=item.strip()))
    elif isinstance(raw_artists, dict):
        name = str(raw_artists.get("name") or "").strip()
        if name:
            artist_id = raw_artists.get("id") or raw_artists.get("browseId")
            refs.append(ArtistRef(id=str(artist_id) if artist_id else None, name=name))
    elif isinstance(raw_artists, str) and raw_artists.strip():
        refs.append(ArtistRef(id=None, name=raw_artists.strip()))

    if not refs and fallback_author and fallback_author.strip():
        refs.append(ArtistRef(id=None, name=fallback_author.strip()))
    return refs


def parse_album_ref(raw_album: Any) -> Optional[AlbumRef]:
    if isinstance(raw_album, dict):
        name = str(raw_album.get("name") or raw_album.get("title") or "").strip()
        if not name:
            return None
        album_id = raw_album.get("id") or raw_album.get("browseId")
        return AlbumRef(id=str(album_id) if album_id else None, name=name)
    if isinstance(raw_album, str) and raw_album.strip():
        return AlbumRef(id=None, name=raw_album.strip())
    return None


def normalize_track(
    item: Dict[str, Any],
    fallback_album: Optional[AlbumRef] = None,
    fallback_thumbnail: Optional[str] = None,
) -> Optional[TrackSummary]:
    if not isinstance(item, dict):
        return None
    video_id = str(item.get("videoId") or "").strip()
    if not video_id:
        return None
    if item.get("isAvailable") is False:
        return None

    title = str(item.get("title") or "").strip()
    if not title or title.lower() in {"deleted video", "private video"}:
        return None

    artists = parse_artists(
        item.get("artists"),
        fallback_author=str(item.get("author") or item.get("artist") or "").strip() or None,
    )
    artist_label = ", ".join(a.name for a in artists) if artists else "Unknown Artist"

    album_ref = parse_album_ref(item.get("album")) or fallback_album
    duration_sec = (
        parse_duration_seconds(item.get("duration_seconds"))
        or parse_duration_seconds(item.get("lengthSeconds"))
        or parse_duration_seconds(item.get("duration"))
        or parse_duration_seconds(item.get("length"))
    )
    duration_text = str(item.get("duration") or item.get("length") or "").strip() or format_duration_text(duration_sec)
    thumb = (
        pick_best_thumbnail(item.get("thumbnails"))
        or pick_best_thumbnail(item.get("thumbnail"))
        or fallback_thumbnail
        or f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
    )
    is_explicit = bool(item.get("isExplicit", False))
    result_type = str(item.get("resultType") or item.get("videoType") or "song").lower()

    return TrackSummary(
        videoId=video_id,
        title=title,
        artist=artist_label,
        artists=artists,
        album=album_ref,
        durationSeconds=duration_sec,
        durationText=duration_text,
        thumbnailUrl=thumb,
        isExplicit=is_explicit,
        resultType=result_type,
    )


def normalize_album_summary(item: Dict[str, Any]) -> Optional[AlbumSummary]:
    if not isinstance(item, dict):
        return None
    browse_id = str(item.get("browseId") or item.get("audioPlaylistId") or "").strip()
    title = str(item.get("title") or "").strip()
    if not browse_id or not title:
        return None
    artists = parse_artists(item.get("artists"))
    year_val = item.get("year")
    year_int: Optional[int] = None
    if year_val is not None and str(year_val).strip().isdigit():
        year_int = int(str(year_val).strip())

    return AlbumSummary(
        browseId=browse_id,
        playlistId=str(item.get("audioPlaylistId") or item.get("playlistId") or "").strip() or None,
        title=title,
        artist=", ".join(a.name for a in artists),
        artists=artists,
        year=year_int,
        thumbnailUrl=pick_best_thumbnail(item.get("thumbnails")),
        isExplicit=bool(item.get("isExplicit", False)),
    )


def normalize_artist_summary(item: Dict[str, Any]) -> Optional[ArtistSummary]:
    if not isinstance(item, dict):
        return None
    browse_id = str(item.get("browseId") or item.get("id") or "").strip()
    name = str(item.get("artist") or item.get("name") or item.get("title") or "").strip()
    if not browse_id or not name:
        return None
    return ArtistSummary(
        browseId=browse_id,
        name=name,
        subscribers=str(item.get("subscribers") or "").strip() or None,
        thumbnailUrl=pick_best_thumbnail(item.get("thumbnails")),
    )


def normalize_playlist_summary(item: Dict[str, Any]) -> Optional[PlaylistSummary]:
    if not isinstance(item, dict):
        return None
    playlist_id = str(item.get("browseId") or item.get("playlistId") or "").strip()
    if playlist_id.startswith("VL") and len(playlist_id) > 4:
        playlist_id = playlist_id[2:]
    title = str(item.get("title") or "").strip()
    if not playlist_id or not title:
        return None
    author_raw = item.get("author")
    author_str: Optional[str] = None
    if isinstance(author_raw, list):
        names = [str(a.get("name") or "") for a in author_raw if isinstance(a, dict) and a.get("name")]
        author_str = ", ".join(names) if names else None
    elif isinstance(author_raw, dict):
        author_str = str(author_raw.get("name") or "").strip() or None
    elif isinstance(author_raw, str):
        author_str = author_raw.strip() or None

    count_raw = item.get("itemCount")
    return PlaylistSummary(
        playlistId=playlist_id,
        title=title,
        author=author_str,
        itemCount=str(count_raw).strip() if count_raw is not None else None,
        thumbnailUrl=pick_best_thumbnail(item.get("thumbnails")),
    )


def normalize_search_results(
    query: str,
    raw_results: List[Dict[str, Any]],
    filter_name: Optional[str] = None,
    suggestions: Optional[List[str]] = None,
    offset: int = 0,
    limit: int = 20,
) -> SearchResponse:
    tracks: List[TrackSummary] = []
    albums: List[AlbumSummary] = []
    artists: List[ArtistSummary] = []
    playlists: List[PlaylistSummary] = []

    seen_videos = set()
    for item in raw_results or []:
        if not isinstance(item, dict):
            continue
        rtype = str(item.get("resultType") or "").lower()
        if rtype in {"song", "video"} or item.get("videoId"):
            track = normalize_track(item)
            if track and track.videoId not in seen_videos:
                seen_videos.add(track.videoId)
                tracks.append(track)
        elif rtype == "album":
            album = normalize_album_summary(item)
            if album:
                albums.append(album)
        elif rtype == "artist":
            artist = normalize_artist_summary(item)
            if artist:
                artists.append(artist)
        elif rtype == "playlist":
            playlist = normalize_playlist_summary(item)
            if playlist:
                playlists.append(playlist)

    next_cont = str(offset + limit) if len(raw_results or []) >= limit else None
    return SearchResponse(
        query=query,
        filter=filter_name,
        items=tracks,
        albums=albums,
        artists=artists,
        playlists=playlists,
        suggestions=suggestions or [],
        continuation=next_cont,
    )


def normalize_song_detail(
    video_id: str,
    raw_song: Dict[str, Any],
    watch_playlist: Optional[Dict[str, Any]] = None,
) -> Optional[SongDetailResponse]:
    video_details = raw_song.get("videoDetails") if isinstance(raw_song, dict) else None
    if not isinstance(video_details, dict):
        video_details = raw_song if isinstance(raw_song, dict) else {}

    playability = raw_song.get("playabilityStatus") if isinstance(raw_song, dict) else {}
    if isinstance(playability, dict):
        status = str(playability.get("status") or "").upper()
        if status in {"LOGIN_REQUIRED", "ERROR", "UNPLAYABLE"} and not video_details.get("title"):
            return None

    resolved_id = str(video_details.get("videoId") or video_id).strip()
    title = str(video_details.get("title") or "").strip()
    if not title and watch_playlist and isinstance(watch_playlist.get("tracks"), list):
        first_watch = next(
            (t for t in watch_playlist["tracks"] if isinstance(t, dict) and t.get("videoId") == resolved_id),
            watch_playlist["tracks"][0] if watch_playlist["tracks"] else None,
        )
        if isinstance(first_watch, dict):
            title = str(first_watch.get("title") or "").strip()

    if not title or title.lower() in {"private video", "deleted video"}:
        return None

    author = str(video_details.get("author") or "").strip()
    artists = parse_artists(video_details.get("artists"), fallback_author=author or None)
    album_ref: Optional[AlbumRef] = None
    lyrics_id: Optional[str] = None
    related_id: Optional[str] = None
    recommended: List[TrackSummary] = []

    if isinstance(watch_playlist, dict):
        lyrics_id = str(watch_playlist.get("lyrics") or "").strip() or None
        related_id = str(watch_playlist.get("related") or "").strip() or None
        for w_track in watch_playlist.get("tracks") or []:
            if not isinstance(w_track, dict):
                continue
            if str(w_track.get("videoId") or "") == resolved_id:
                if not artists:
                    artists = parse_artists(w_track.get("artists"), fallback_author=author or None)
                album_ref = album_ref or parse_album_ref(w_track.get("album"))
                continue
            norm_rec = normalize_track(w_track)
            if norm_rec:
                recommended.append(norm_rec)

    duration_sec = parse_duration_seconds(video_details.get("lengthSeconds") or video_details.get("durationSeconds"))
    thumb = (
        pick_best_thumbnail(video_details.get("thumbnail"))
        or pick_best_thumbnail(video_details.get("thumbnails"))
        or f"https://i.ytimg.com/vi/{resolved_id}/hqdefault.jpg"
    )
    artist_label = ", ".join(a.name for a in artists) if artists else (author or "Unknown Artist")

    return SongDetailResponse(
        videoId=resolved_id,
        title=title,
        artist=artist_label,
        artists=artists,
        album=album_ref,
        durationSeconds=duration_sec,
        thumbnailUrl=thumb,
        isExplicit=bool(video_details.get("isExplicit", False)),
        lyricsBrowseId=lyrics_id,
        relatedBrowseId=related_id,
        recommendedTracks=recommended,
        streamingAuthorized=False,
    )


def normalize_lyrics(video_id: str, raw_lyrics: Optional[Dict[str, Any]]) -> LyricsResponse:
    if not raw_lyrics or not isinstance(raw_lyrics, dict):
        return LyricsResponse(videoId=video_id, available=False)

    has_timestamps = bool(raw_lyrics.get("hasTimestamps", False))
    raw_entries = raw_lyrics.get("lyrics")
    source = str(raw_lyrics.get("source") or "").strip() or None
    lines: List[LyricLine] = []
    text_parts: List[str] = []

    if isinstance(raw_entries, list):
        for entry in raw_entries:
            if isinstance(entry, dict):
                text = str(entry.get("text") or "").strip()
                start_ms = entry.get("start_time") or entry.get("startTimeMs")
                end_ms = entry.get("end_time") or entry.get("endTimeMs")
                lines.append(
                    LyricLine(
                        startTimeMs=int(start_ms) if isinstance(start_ms, (int, float)) else None,
                        endTimeMs=int(end_ms) if isinstance(end_ms, (int, float)) else None,
                        text=text,
                    )
                )
                if start_ms is not None and isinstance(start_ms, (int, float)):
                    has_timestamps = True
                    mm = int(start_ms) // 60000
                    ss = (int(start_ms) % 60000) / 1000.0
                    text_parts.append(f"[{mm:02d}:{ss:05.2f}]{text}")
                else:
                    text_parts.append(text)
            else:
                text = str(getattr(entry, "text", entry) or "").strip()
                start_ms = getattr(entry, "start_time", None)
                end_ms = getattr(entry, "end_time", None)
                lines.append(
                    LyricLine(
                        startTimeMs=int(start_ms) if isinstance(start_ms, (int, float)) else None,
                        endTimeMs=int(end_ms) if isinstance(end_ms, (int, float)) else None,
                        text=text,
                    )
                )
                if start_ms is not None and isinstance(start_ms, (int, float)):
                    has_timestamps = True
                    mm = int(start_ms) // 60000
                    ss = (int(start_ms) % 60000) / 1000.0
                    text_parts.append(f"[{mm:02d}:{ss:05.2f}]{text}")
                else:
                    text_parts.append(text)
    elif isinstance(raw_entries, str) and raw_entries.strip():
        raw_str = raw_entries.strip()
        for raw_line in raw_str.splitlines():
            m = LRC_TIMESTAMP_RE.match(raw_line.strip())
            if m:
                has_timestamps = True
                mins = int(m.group(1))
                secs = int(m.group(2))
                frac = m.group(3) or "0"
                ms = int(frac.ljust(3, "0")[:3])
                start_ms = mins * 60000 + secs * 1000 + ms
                lyric_text = m.group(4).strip()
                lines.append(LyricLine(startTimeMs=start_ms, endTimeMs=None, text=lyric_text))
            else:
                lines.append(LyricLine(startTimeMs=None, endTimeMs=None, text=raw_line.strip()))
        text_parts = [raw_str]

    joined_text = "\n".join(text_parts).strip()
    available = bool(joined_text or lines)
    return LyricsResponse(
        videoId=video_id,
        available=available,
        hasTimestamps=has_timestamps and any(l.startTimeMs is not None for l in lines),
        lyricsText=joined_text,
        lines=lines,
        source=source,
    )


def normalize_album_detail(browse_id: str, raw_album: Dict[str, Any]) -> Optional[AlbumDetailResponse]:
    if not isinstance(raw_album, dict):
        return None
    title = str(raw_album.get("title") or "").strip()
    if not title:
        return None
    artists = parse_artists(raw_album.get("artists"))
    album_ref = AlbumRef(id=browse_id, name=title)
    thumb = pick_best_thumbnail(raw_album.get("thumbnails"))
    year_raw = raw_album.get("year")
    year_int = int(str(year_raw)) if year_raw is not None and str(year_raw).isdigit() else None

    tracks: List[TrackSummary] = []
    for t in raw_album.get("tracks") or []:
        norm = normalize_track(t, fallback_album=album_ref, fallback_thumbnail=thumb)
        if norm:
            tracks.append(norm)

    return AlbumDetailResponse(
        browseId=browse_id,
        playlistId=str(raw_album.get("audioPlaylistId") or "").strip() or None,
        title=title,
        artist=", ".join(a.name for a in artists),
        artists=artists,
        year=year_int,
        description=str(raw_album.get("description") or "").strip() or None,
        thumbnailUrl=thumb,
        trackCount=int(raw_album.get("trackCount") or len(tracks)),
        durationSeconds=parse_duration_seconds(raw_album.get("duration_seconds")),
        tracks=tracks,
    )


def normalize_artist_detail(browse_id: str, raw_artist: Dict[str, Any]) -> Optional[ArtistDetailResponse]:
    if not isinstance(raw_artist, dict):
        return None
    name = str(raw_artist.get("name") or "").strip()
    if not name:
        return None

    songs_section = raw_artist.get("songs") or {}
    top_songs: List[TrackSummary] = []
    if isinstance(songs_section, dict):
        for s in songs_section.get("results") or []:
            norm = normalize_track(s)
            if norm:
                top_songs.append(norm)

    albums_section = raw_artist.get("albums") or {}
    albums: List[AlbumSummary] = []
    if isinstance(albums_section, dict):
        for a in albums_section.get("results") or []:
            norm_a = normalize_album_summary(a)
            if norm_a:
                albums.append(norm_a)

    singles_section = raw_artist.get("singles") or {}
    singles: List[AlbumSummary] = []
    if isinstance(singles_section, dict):
        for s in singles_section.get("results") or []:
            norm_s = normalize_album_summary(s)
            if norm_s:
                singles.append(norm_s)

    related_section = raw_artist.get("related") or {}
    related: List[ArtistSummary] = []
    if isinstance(related_section, dict):
        for r in related_section.get("results") or []:
            norm_r = normalize_artist_summary(r)
            if norm_r:
                related.append(norm_r)

    return ArtistDetailResponse(
        browseId=browse_id,
        name=name,
        description=str(raw_artist.get("description") or "").strip() or None,
        subscribers=str(raw_artist.get("subscribers") or "").strip() or None,
        views=str(raw_artist.get("views") or "").strip() or None,
        thumbnailUrl=pick_best_thumbnail(raw_artist.get("thumbnails")),
        shufflePlaylistId=str(raw_artist.get("shuffleId") or "").strip() or None,
        radioPlaylistId=str(raw_artist.get("radioId") or "").strip() or None,
        topSongs=top_songs,
        albums=albums,
        singles=singles,
        relatedArtists=related,
    )


def normalize_playlist_detail(
    playlist_id: str,
    raw_playlist: Dict[str, Any],
    offset: int = 0,
    limit: int = 50,
) -> Optional[PlaylistDetailResponse]:
    if not isinstance(raw_playlist, dict):
        return None
    clean_id = playlist_id[2:] if playlist_id.startswith("VL") and len(playlist_id) > 4 else playlist_id
    title = str(raw_playlist.get("title") or "").strip()
    if not title:
        return None

    author_refs = parse_artists(raw_playlist.get("author"))
    author_ref = author_refs[0] if author_refs else None
    thumb = pick_best_thumbnail(raw_playlist.get("thumbnails"))

    all_tracks: List[TrackSummary] = []
    for t in raw_playlist.get("tracks") or []:
        norm = normalize_track(t, fallback_thumbnail=thumb)
        if norm:
            all_tracks.append(norm)

    sliced = all_tracks[offset : offset + limit] if offset > 0 or len(all_tracks) > limit else all_tracks
    next_cont = str(offset + limit) if offset + limit < len(all_tracks) else None

    return PlaylistDetailResponse(
        playlistId=str(raw_playlist.get("id") or clean_id),
        title=title,
        author=author_ref,
        description=str(raw_playlist.get("description") or "").strip() or None,
        trackCount=int(raw_playlist.get("trackCount") or len(all_tracks)),
        durationText=str(raw_playlist.get("duration") or "").strip() or None,
        thumbnailUrl=thumb,
        tracks=sliced,
        continuation=next_cont,
    )


def normalize_radio(video_id: str, raw_watch: Dict[str, Any]) -> RadioResponse:
    tracks: List[TrackSummary] = []
    seen = set()
    for t in (raw_watch or {}).get("tracks") or []:
        norm = normalize_track(t)
        if norm and norm.videoId not in seen:
            seen.add(norm.videoId)
            tracks.append(norm)

    return RadioResponse(
        videoId=video_id,
        playlistId=str((raw_watch or {}).get("playlistId") or "").strip() or None,
        lyricsBrowseId=str((raw_watch or {}).get("lyrics") or "").strip() or None,
        tracks=tracks,
    )

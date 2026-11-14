from __future__ import annotations

import copy
from typing import Any, Callable, List, Optional

try:
    from pydantic import BaseModel, Field
    HAS_PYDANTIC = True
except ImportError:
    HAS_PYDANTIC = False

    class _FieldDefault:
        def __init__(self, default: Any = None, default_factory: Optional[Callable[[], Any]] = None):
            self.default = default
            self.default_factory = default_factory

    def Field(default: Any = None, default_factory: Optional[Callable[[], Any]] = None, **kwargs: Any) -> Any:
        return _FieldDefault(default=default, default_factory=default_factory)

    class BaseModel:
        def __init__(self, **kwargs: Any):
            for cls in reversed(self.__class__.__mro__):
                if cls is object or cls is BaseModel:
                    continue
                for k, v in cls.__dict__.items():
                    if k.startswith("_") or callable(v) or isinstance(v, (classmethod, staticmethod)):
                        continue
                    if isinstance(v, _FieldDefault):
                        setattr(self, k, v.default_factory() if v.default_factory else copy.deepcopy(v.default))
                    else:
                        setattr(self, k, copy.deepcopy(v))
            for k, v in kwargs.items():
                setattr(self, k, v)

        def model_dump(self) -> dict[str, Any]:
            def _dump(val: Any) -> Any:
                if isinstance(val, BaseModel):
                    return val.model_dump()
                if isinstance(val, list):
                    return [_dump(x) for x in val]
                if isinstance(val, dict):
                    return {dk: _dump(dv) for dk, dv in val.items()}
                return val

            return {k: _dump(v) for k, v in self.__dict__.items() if not k.startswith("_")}

        @classmethod
        def model_rebuild(cls) -> None:
            pass


class ArtistRef(BaseModel):
    id: Optional[str] = None
    name: str = ""


class AlbumRef(BaseModel):
    id: Optional[str] = None
    name: str = ""


class TrackSummary(BaseModel):
    videoId: str = ""
    title: str = ""
    artist: str = "Unknown Artist"
    artists: List[ArtistRef] = Field(default_factory=list)
    album: Optional[AlbumRef] = None
    durationSeconds: Optional[int] = None
    durationText: Optional[str] = None
    thumbnailUrl: Optional[str] = None
    isExplicit: bool = False
    resultType: str = "song"


class AlbumSummary(BaseModel):
    browseId: str = ""
    playlistId: Optional[str] = None
    title: str = ""
    artist: str = ""
    artists: List[ArtistRef] = Field(default_factory=list)
    year: Optional[int] = None
    thumbnailUrl: Optional[str] = None
    isExplicit: bool = False


class ArtistSummary(BaseModel):
    browseId: str = ""
    name: str = ""
    subscribers: Optional[str] = None
    thumbnailUrl: Optional[str] = None


class PlaylistSummary(BaseModel):
    playlistId: str = ""
    title: str = ""
    author: Optional[str] = None
    itemCount: Optional[str] = None
    thumbnailUrl: Optional[str] = None


class SearchResponse(BaseModel):
    query: str = ""
    filter: Optional[str] = None
    items: List[TrackSummary] = Field(default_factory=list)
    albums: List[AlbumSummary] = Field(default_factory=list)
    artists: List[ArtistSummary] = Field(default_factory=list)
    playlists: List[PlaylistSummary] = Field(default_factory=list)
    suggestions: List[str] = Field(default_factory=list)
    continuation: Optional[str] = None


class SuggestionsResponse(BaseModel):
    query: str = ""
    suggestions: List[str] = Field(default_factory=list)


class SongDetailResponse(BaseModel):
    videoId: str = ""
    title: str = ""
    artist: str = "Unknown Artist"
    artists: List[ArtistRef] = Field(default_factory=list)
    album: Optional[AlbumRef] = None
    durationSeconds: Optional[int] = None
    thumbnailUrl: Optional[str] = None
    isExplicit: bool = False
    lyricsBrowseId: Optional[str] = None
    relatedBrowseId: Optional[str] = None
    recommendedTracks: List[TrackSummary] = Field(default_factory=list)
    # Explicitly metadata-only: never exposes audio stream URLs
    streamingAuthorized: bool = False


class LyricLine(BaseModel):
    startTimeMs: Optional[int] = None
    endTimeMs: Optional[int] = None
    text: str = ""


class LyricsResponse(BaseModel):
    videoId: str = ""
    available: bool = False
    hasTimestamps: bool = False
    lyricsText: str = ""
    lines: List[LyricLine] = Field(default_factory=list)
    source: Optional[str] = None


class AlbumDetailResponse(BaseModel):
    browseId: str = ""
    playlistId: Optional[str] = None
    title: str = ""
    artist: str = ""
    artists: List[ArtistRef] = Field(default_factory=list)
    year: Optional[int] = None
    description: Optional[str] = None
    thumbnailUrl: Optional[str] = None
    trackCount: int = 0
    durationSeconds: Optional[int] = None
    tracks: List[TrackSummary] = Field(default_factory=list)


class ArtistDetailResponse(BaseModel):
    browseId: str = ""
    name: str = ""
    description: Optional[str] = None
    subscribers: Optional[str] = None
    views: Optional[str] = None
    thumbnailUrl: Optional[str] = None
    shufflePlaylistId: Optional[str] = None
    radioPlaylistId: Optional[str] = None
    topSongs: List[TrackSummary] = Field(default_factory=list)
    albums: List[AlbumSummary] = Field(default_factory=list)
    singles: List[AlbumSummary] = Field(default_factory=list)
    relatedArtists: List[ArtistSummary] = Field(default_factory=list)


class PlaylistDetailResponse(BaseModel):
    playlistId: str = ""
    title: str = ""
    author: Optional[ArtistRef] = None
    description: Optional[str] = None
    trackCount: Optional[int] = None
    durationText: Optional[str] = None
    thumbnailUrl: Optional[str] = None
    tracks: List[TrackSummary] = Field(default_factory=list)
    continuation: Optional[str] = None


class RadioResponse(BaseModel):
    videoId: str = ""
    playlistId: Optional[str] = None
    lyricsBrowseId: Optional[str] = None
    tracks: List[TrackSummary] = Field(default_factory=list)


SearchResponse.model_rebuild()

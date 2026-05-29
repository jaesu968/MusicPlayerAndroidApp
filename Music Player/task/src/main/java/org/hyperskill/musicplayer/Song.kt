package org.hyperskill.musicplayer

// model to represent a song

// data class to represent a song with id, title, artist, and duration
data class Song (
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long
)

// enum class to represent the state of a track: playing, paused, stopped
enum class TrackState { PLAYING, PAUSED, STOPPED }

// data class to represent a track with a song and its current state
data class Track(
    val song: Song,
    var state: TrackState
)

// data class SongSelector to represent a song in the playlist selection mode, with an additional isSelected property
data class SongSelector(
    val song: Song,
    var isSelected: Boolean
)


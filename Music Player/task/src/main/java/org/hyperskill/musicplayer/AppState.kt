package org.hyperskill.musicplayer

// PLAY_MUSIC
// mainSongList uses list_item_song
// mainFragmentContainer uses MainPlayerControllerFragment
// displayed list = currentPlaylist

// ADD_PLAYLIST
// mainSongList uses list_itemn_song_selector
// mainFragmentContainer uses MainAddPlaylistFragment
// display list = loaded playlist mapped to SongSelector

enum class AppState {
    PLAY_MUSIC, ADD_PLAYLIST
}
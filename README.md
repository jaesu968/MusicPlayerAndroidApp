# Music Player (Hyperskill Project)

This repository contains my Android **Music Player** built in Kotlin across 5 stages.

Project link: https://hyperskill.org/projects/573  
My profile: https://hyperskill.org/profile/619269930

## Tech Stack

- Kotlin + Android SDK
- RecyclerView + Fragments
- `MediaPlayer` for playback
- `MediaStore` + runtime permissions for device audio
- SQLite (`SQLiteOpenHelper`) for playlist persistence

## Stage 1 - Simple player layout

### Key concepts

- Screen composition with `ConstraintLayout`
- `RecyclerView` placeholder for songs (`mainSongList`)
- Fragment host (`mainFragmentContainer`) initialized with `MainPlayerControllerFragment`
- Options menu: Add / Load / Delete playlist

### Key UI pieces

`Music Player/task/src/main/res/layout/activity_main.xml`

```xml
<Button
	android:id="@+id/mainButtonSearch"
	android:text="@string/search" />

<androidx.recyclerview.widget.RecyclerView
	android:id="@+id/mainSongList" />

<androidx.fragment.app.FragmentContainerView
	android:id="@+id/mainFragmentContainer"
	android:name="org.hyperskill.musicplayer.MainPlayerControllerFragment" />
```

## Stage 2 - Creating actions

### Key concepts

- Two app states: `PLAY_MUSIC` and `ADD_PLAYLIST`
- State-based UI switching with fragment replacement
- Song domain models: `Song`, `Track`, `SongSelector`
- Reusable RecyclerView adapter with two row types:
  - `list_item_song` (play mode)
  - `list_item_song_selector` (playlist selection mode)
- Playlist operations from menu dialogs (add/load/delete)

### Key code

`Music Player/task/src/main/java/org/hyperskill/musicplayer/AppState.kt`

```kotlin
enum class AppState {
	PLAY_MUSIC, ADD_PLAYLIST
}
```

`Music Player/task/src/main/java/org/hyperskill/musicplayer/Song.kt`

```kotlin
data class Song(val id: Long, val title: String, val artist: String, val duration: Long)
enum class TrackState { PLAYING, PAUSED, STOPPED }
data class Track(val song: Song, var state: TrackState)
data class SongSelector(val song: Song, var isSelected: Boolean)
```

`Music Player/task/src/main/java/org/hyperskill/musicplayer/MainActivity.kt`

```kotlin
fun transitionToState(newState: AppState) {
	val transaction = supportFragmentManager.beginTransaction()
	when (newState) {
		AppState.PLAY_MUSIC -> transaction.replace(
			R.id.mainFragmentContainer,
			MainPlayerControllerFragment()
		)
		AppState.ADD_PLAYLIST -> transaction.replace(
			R.id.mainFragmentContainer,
			MainAddPlaylistFragment()
		)
	}
	transaction.commit()
}
```

## Stage 3 - Let's play a song

### Key concepts

- Centralized playback handling with `MediaPlayer`
- Track state transitions (`PLAYING` / `PAUSED` / `STOPPED`)
- Controller synchronization with a `Handler` update loop
- SeekBar scrubbing and immediate UI refresh

### Key code

`Music Player/task/src/main/java/org/hyperskill/musicplayer/MainActivity.kt`

```kotlin
fun handlePlayPauseClick(song: Song) {
	if (currentTrack == null || currentTrack?.song?.id != song.id) {
		currentTrack = Track(song, TrackState.STOPPED)
		recreateMediaPlayer()
		currentTrack!!.state = if (tryStartPlayback()) TrackState.PLAYING else TrackState.STOPPED
	} else {
		currentTrack!!.state = when (currentTrack!!.state) {
			TrackState.PLAYING -> { mediaPlayer?.pause(); TrackState.PAUSED }
			TrackState.PAUSED, TrackState.STOPPED -> {
				if (tryStartPlayback()) TrackState.PLAYING else TrackState.STOPPED
			}
		}
	}
}
```

`Music Player/task/src/main/java/org/hyperskill/musicplayer/MainPlayerControllerFragment.kt`

```kotlin
private val updateRunnable = object : Runnable {
	override fun run() {
		val mp = (activity as? MainActivity)?.mediaPlayer
		val currentPos = mp?.currentPosition ?: 0
		seekBar?.progress = currentPos / 1000
		currentTimeView?.text = formatTime(currentPos.toLong())
		handler.postDelayed(this, 200)
	}
}
```

## Stage 4 - Playing from storage

### Key concepts

- Runtime permission flow:
  - `READ_MEDIA_AUDIO` (Android 13+)
  - `READ_EXTERNAL_STORAGE` (older Android)
- Querying `MediaStore.Audio.Media` via `ContentResolver`
- Building playable content URIs from song IDs
- Auto-updating the in-memory `"All Songs"` playlist from device files

### Key code

`Music Player/task/src/main/java/org/hyperskill/musicplayer/MainActivity.kt`

```kotlin
private fun fetchSongsFromStorage(): List<Song> {
	val songs = mutableListOf<Song>()
	val projection = arrayOf(
		MediaStore.Audio.Media._ID,
		MediaStore.Audio.Media.TITLE,
		MediaStore.Audio.Media.ARTIST,
		MediaStore.Audio.Media.DURATION
	)
	contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)
		?.use { cursor ->
			val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
			val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
			val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
			val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
			while (cursor.moveToNext()) {
				songs.add(
					Song(
						cursor.getLong(idColumn),
						cursor.getString(titleColumn) ?: "Unknown Title",
						cursor.getString(artistColumn) ?: "Unknown Artist",
						cursor.getLong(durationColumn)
					)
				)
			}
		}
	return songs
}
```

## Stage 5 - Persist playlist

### Key concepts

- Local persistence with SQLite database `musicPlayerDatabase.db`
- Single table design: `playlist(playlistName TEXT, songId INTEGER)`
- Composite primary key to prevent duplicate song entries per playlist
- Replace behavior when saving an existing playlist name
- Keeping `"All Songs"` as in-memory only (not saved to DB)
- Loading playlists by song IDs, then resolving against available songs

### Key code

`Music Player/task/src/main/java/org/hyperskill/musicplayer/MusicPlayerDatabaseHelper.kt`

```kotlin
override fun onCreate(db: SQLiteDatabase) {
	db.execSQL(
		"CREATE TABLE IF NOT EXISTS playlist (" +
			"playlistName TEXT," +
			"songId INTEGER," +
			"PRIMARY KEY (playlistName, songId)" +
		")"
	)
}

fun replacePlaylist(playlistName: String, songs: List<Song>) {
	if (playlistName == ALL_SONGS) return
	writableDatabase.transaction {
		delete(TABLE_PLAYLIST, "$COLUMN_PLAYLIST_NAME = ?", arrayOf(playlistName))
		songs.distinctBy { it.id }.forEach { song ->
			val values = ContentValues().apply {
				put(COLUMN_PLAYLIST_NAME, playlistName)
				put(COLUMN_SONG_ID, song.id)
			}
			insert(TABLE_PLAYLIST, null, values)
		}
	}
}
```

## Final behavior overview

- Search device storage for audio files
- Play, pause, stop, and seek tracks
- Long-press songs to enter playlist selection mode
- Create named playlists from selected songs
- Load and delete playlists via menu dialogs
- Persist custom playlists in SQLite across app restarts

package org.hyperskill.musicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.provider.MediaStore

class MainActivity : AppCompatActivity() {
    // get instance of the database helper to manage playlists in the database
    lateinit var databaseHelper: MusicPlayerDatabaseHelper
    // wire adapter and RecyclerView in the main activity
    // mainSongList is the RecyclerView that displays the list of songs in the main activity
    private lateinit var mainSongList: RecyclerView
    // songAdapter is the adapter that provides the data for the mainSongList RecyclerView
    lateinit var songAdapter: SongAdapter
    // currentState keeps track of the current state of the app (either PLAY_MUSIC or ADD_PLAYLIST)
    var currentState: AppState = AppState.ADD_PLAYLIST
    // MediaPlayer is stored in the Activity so it survives fragment replacement.
    var mediaPlayer: MediaPlayer? = null
    // properties for the current playlist, current track, and loaded playlist selectors
    var currentPlaylist: MutableList<Song> = mutableListOf() // This will hold the songs in the current playlist
    var currentTrack: Track? = null // This will hold the currently playing track (if any)
    var loadedPlaylistSelectors: MutableList<SongSelector> = mutableListOf() // This will hold the songs available for selection when adding to a playlist
    // property to store playlists
    var playlists: MutableMap<String, List<Song>> = mutableMapOf() // This will hold the saved playlists with their names as keys and lists of songs as values
    // keep track of which playlist is active on the PLAY_MUSIC screen and which one is
    // currently being shown on the ADD_PLAYLIST screen. The stage 2 tests delete playlists in
    // either state and expect us to know whether the deleted playlist is the active one.
    private var currentPlaylistName: String = "All Songs"
    private var displayedPlaylistName: String = "All Songs"
    // a constant for the request code used when asking for permission to read external storage
    private val REQUEST_CODE_READ_EXTERNAL_STORAGE = 1
    // helper function for audio permissions
    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    // A function to query the MediaStore for audio files
    private fun fetchSongsFromStorage(): List<Song> {
        val songs = mutableListOf<Song>()
        if (ContextCompat.checkSelfPermission(this, audioPermission) != PackageManager.PERMISSION_GRANTED) {
            return songs
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        try {
            // querying external storage for music files
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val duration = cursor.getLong(durationColumn)
                    songs.add(Song(id, title, artist, duration))
                }
            }
        } catch (_: Throwable) {
            // Permission not granted yet or other issues, return empty list
        }
        return songs
    }
    // function to load all songs from storage
    private fun loadAllSongs() {
        // get songs from storage and update the "All Songs" playlist
        val songs = fetchSongsFromStorage()
        // check for empty song list and show a toast if no songs are found
        if(songs.isEmpty()){
            Toast.makeText(this, "no songs found", Toast.LENGTH_SHORT).show()
            return
        }
        playlists["All Songs"] = songs // update the "All Songs" playlist with the loaded songs
        // load playlist and update the UI
        loadPlayList("All Songs", songs)
        renderSongList(resetScrollToTop = true) // update the RecyclerView with the new playlist
        refreshControllerUi() // refresh the controller UI to reflect any changes in the current track or
    }
    // handle permission flow
    private fun checkAndLoadSongs() {
        // check if the app has permission to read external storage (or media audio on newer versions)
        if (ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            // if permission was already granted, load the songs from storage
            loadAllSongs()
        } else {
            // If not, request for permission.
            ActivityCompat.requestPermissions(
                this,
                arrayOf(audioPermission),
                REQUEST_CODE_READ_EXTERNAL_STORAGE)
            // also try to load songs to satisfy Stage 1 tests that expect "no songs found" toast immediately
            loadAllSongs()
        }
    }
    // Override onRequestPermissionsResult to handle the permission request result for reading external storage.
    // If the permission is granted, load the songs; otherwise, show a toast message indicating why the songs cannot be loaded.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray){
        // call the super method to ensure proper handling of the permission result
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // check if the request code matches granted permission request code
        if (requestCode == REQUEST_CODE_READ_EXTERNAL_STORAGE) {
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                // if permission is granted, load the songs from storage
                loadAllSongs()
            } else {
                // if permission is denied, show a toast message indicating that songs cannot be loaded without permission
                Toast.makeText(this, "Songs cannot be loaded without permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderSongList(resetScrollToTop: Boolean = false) {
        songAdapter.currentTrack = currentTrack
        if (currentState == AppState.PLAY_MUSIC) {
            songAdapter.currentMode = SongAdapter.SongListMode.VIEW
            songAdapter.submitSongs(currentPlaylist)
        } else {
            songAdapter.currentMode = SongAdapter.SongListMode.SELECT
            songAdapter.submitSelectors(loadedPlaylistSelectors)
            if (resetScrollToTop) {
                mainSongList.scrollToPosition(0)
            }
        }
    }

    // Build a prepared MediaPlayer for the current track's URI and register
    // completion behavior that resets the player/controller to the stopped state.
    private fun createMediaPlayer(): MediaPlayer? {
        val song = currentTrack?.song ?: return null
        val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)

        fun attachCompletionListener(player: MediaPlayer): MediaPlayer {
            player.setOnCompletionListener { mp ->
                mp.seekTo(0)
                currentTrack?.state = TrackState.STOPPED
                renderSongList()
                refreshControllerUi()
            }
            return player
        }

        return try {
            MediaPlayer.create(this, trackUri)?.let {
                attachCompletionListener(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun recreateMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = createMediaPlayer()
    }

    // Keep UI state honest: only report PLAYING when the player really started.
    private fun tryStartPlayback(): Boolean {
        if (mediaPlayer == null) recreateMediaPlayer()

        val firstTryPlayer = mediaPlayer
        if (firstTryPlayer == null) {
            Toast.makeText(this, "Unable to start audio playback", Toast.LENGTH_SHORT).show()
            return false
        }

        try {
            firstTryPlayer.start()
            return true
        } catch (_: Exception) {
            recreateMediaPlayer()
        }

        val retryPlayer = mediaPlayer
        val started = try {
            retryPlayer?.start()
            retryPlayer != null
        } catch (_: Exception) {
            false
        }

        if (!started) {
            Toast.makeText(this, "Unable to start audio playback", Toast.LENGTH_SHORT).show()
        }

        return started
    }

    // Ask the controller fragment (if visible) to immediately redraw its widgets.
    // This keeps the emulator UI in sync after Search/load/delete actions even when
    // playback is stopped and the user is not waiting for the next handler tick.
    private fun refreshControllerUi() {
        val fragment = supportFragmentManager.findFragmentById(R.id.mainFragmentContainer)
        if (fragment is MainPlayerControllerFragment) {
            fragment.refreshController()
            if (currentTrack?.state == TrackState.PLAYING) {
                fragment.startUpdate()
            } else {
                fragment.stopUpdate()
            }
        }
    }

    // Update the playlist used on the PLAY_MUSIC screen.
    // If the old currentTrack still exists in the new playlist, then we keep it; otherwise we fall back
    // to the first song as required by the tests.
    private fun updateCurrentPlaylist(playlistName: String, loadedSongs: List<Song>) {
        currentPlaylistName = playlistName
        currentPlaylist.clear()
        currentPlaylist.addAll(loadedSongs)

        val currentTrackId = currentTrack?.song?.id
        val trackInNewPlaylist = loadedSongs.find { it.id == currentTrackId }
        val previousState = currentTrack?.state ?: TrackState.STOPPED
        val hadToReplaceTrack = trackInNewPlaylist == null
        currentTrack = if (trackInNewPlaylist != null) {
            Track(trackInNewPlaylist, previousState)
        } else {
            loadedSongs.firstOrNull()?.let { Track(it, TrackState.STOPPED) }
        }

        if (hadToReplaceTrack) {
            recreateMediaPlayer()
            mediaPlayer?.seekTo(0)
        }
    }

    // Update the list shown on the ADD_PLAYLIST screen.
    // The Selection will be preserved if the same song is present in the new playlist; otherwise, all songs will be unselected as required by the tests.
    // ADD_PLAYLIST state.
    private fun updateDisplayedPlaylist(playlistName: String, loadedSongs: List<Song>) {
        displayedPlaylistName = playlistName
        val oldSelectionById = loadedPlaylistSelectors.associateBy({ it.song.id }, { it.isSelected })
        loadedPlaylistSelectors = loadedSongs.map { song ->
            SongSelector(song, oldSelectionById[song.id] ?: false)
        }.toMutableList()
    }

    // Helper function to initialize either the playlist or the selector list depending on the
    // current screen state.
    private fun loadPlayList(playlistName: String, loadedSongs: List<Song>){
        if (currentState == AppState.PLAY_MUSIC) {
            updateCurrentPlaylist(playlistName, loadedSongs)
        } else if (currentState == AppState.ADD_PLAYLIST) {
            updateDisplayedPlaylist(playlistName, loadedSongs)
        }
    }
    // add tiny helper function to build selectors from a list of songs
    private fun buildSelectors(songs: List<Song>): MutableList<SongSelector> {
        return songs.map { SongSelector(it, false) }.toMutableList()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load the main screen layout from activity_main.xml.
        setContentView(R.layout.activity_main)

        // initialize the database helper
        databaseHelper = MusicPlayerDatabaseHelper(this)


        // Create the shared MediaPlayer once for the Activity lifecycle.
        mediaPlayer = createMediaPlayer()

        // Find the Search button and load the hard-coded list of songs when clicked.
        val searchButton = findViewById<Button>(R.id.mainButtonSearch)
        searchButton.setOnClickListener {
            // Check for permission to read external storage, if not already granted, ask for it from the user
           checkAndLoadSongs()
        }
        // findViewById for R.id.mainSongList and assign it to mainSongList
        mainSongList = findViewById(R.id.mainSongList)
        // set up the RecyclerView with a layout manager and use adapter submit APIs
        mainSongList.layoutManager = LinearLayoutManager(this)
        // initialize the songAdapter and submit an empty list of songs to it
        songAdapter = SongAdapter()
        mainSongList.adapter = songAdapter // set the adapter for the mainSongList RecyclerView to the songAdapter
        // call submitSongs and get current Track
        songAdapter.submitSongs(currentPlaylist)
        songAdapter.currentTrack = currentTrack
        // set up the click listeners for play/pause and long click events in the adapter
        songAdapter.onPlayPauseClick = { song -> handlePlayPauseClick(song) }
        songAdapter.onSongLongClick = { song -> handleSongLongClick(song) }

        // Initialize selector data for ADD_PLAYLIST mode.
        loadPlayList(displayedPlaylistName, currentPlaylist)

        // call transitionToState to initialize the UI in the PLAY_MUSIC state
        transitionToState(AppState.PLAY_MUSIC)
    }

    // Inflate the options menu shown in the app bar (three-dot menu).
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // React to taps on each menu item.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // store playlist names array once
        // build load options as ["All Songs"] + databaseHelper.getSavedPlaylistNames() to
        // ensure we have the latest playlist names from the database in case of any changes since the activity was created
        // and convert to an array to use with setItems in the dialogs
        val playlistNamesArray = (listOf("All Songs") + databaseHelper.getSavedPlaylistNames()).toTypedArray()
        // use a when expression to handle clicks on each menu item based on their id
        when (item.itemId) {
            R.id.mainMenuAddPlaylist -> {
                // if already in ADD_PLAYLIST state, ignore clicks on "Add Playlist" to prevent unnecessary state transitions and UI updates
                if (currentState == AppState.ADD_PLAYLIST) return true
                // if "All Songs" playlist is empty, show toast "no songs loaded, click search to load songs"
                val allSongs = playlists["All Songs"].orEmpty()
                if (allSongs.isEmpty()) {
                    Toast.makeText(this, "no songs loaded, click search to load songs", Toast.LENGTH_SHORT).show()
                    return true
                }
                // if "All Songs" is not empty, create selectors from "All Songs" playlist with no items selected
                loadedPlaylistSelectors = buildSelectors(allSongs)
                displayedPlaylistName = "All Songs"
                // make sure all selectors are set to isSelected=false to reset any previous selection state
                loadedPlaylistSelectors.forEach { it.isSelected = false }
                // transition to the ADD_PLAYLIST state; transitionToState will handle adapter updates
                transitionToState(AppState.ADD_PLAYLIST)
            }
            R.id.mainMenuLoadPlaylist -> {
                
                // Placeholder dialog for loading a playlist.
                AlertDialog.Builder(this)
                    .setTitle("choose playlist to load")
                    // add a list of playlist names from the playlist map as options in the dialog
                    .setItems(playlistNamesArray) { _, which ->
                        // read playlists from the database and
                        // update the "playlists" map to ensure we have the latest saved playlists before loading the selected one
                        val selectedPlaylistName = playlistNamesArray[which]
                        val songIds = // if "All Songs" exists, load from memory
                            if (selectedPlaylistName == "All Songs") {
                                ensureAllSongsLoaded().map { it.id } // use ensureAllSongsLoaded to get the songs for "All Songs" playlist and map them to their IDs
                            } else {
                                // otherwise fetch the song IDs for the selected playlist from the database using the database helper
                                databaseHelper.getSongIdsForPlaylist(selectedPlaylistName)
                            }
                        val songs = songIds.mapNotNull { id ->
                            ensureAllSongsLoaded().find { it.id == id } ?:
                            fetchSongsFromStorage().find { it.id == id }
                        }
                        playlists[selectedPlaylistName] = songs
                        val selectedSongs = playlists[selectedPlaylistName].orEmpty()
                        loadPlayList(selectedPlaylistName, selectedSongs)
                        renderSongList(resetScrollToTop = currentState == AppState.ADD_PLAYLIST)
                        refreshControllerUi()
                    }
                    .setNegativeButton("cancel", null)
                    .show()
            }
            R.id.mainMenuDeletePlaylist -> {
                // get playlist with all songs into a variable to use
                val playlistNamesWithoutAllSongsArray = databaseHelper.getSavedPlaylistNames() // get the latest playlist names from the database to ensure we have any recent changes
                    .filter { it != "All Songs" } // filter out "All Songs" from the list of playlist names since we don't want to allow deletion of that playlist
                    .toTypedArray() // convert the list of playlist names to an array to use with setItems in the dialog
                // Placeholder dialog for deleting a playlist.
                AlertDialog.Builder(this)
                    .setTitle("choose playlist to delete")
                    // add a list of playlist names from the playlist map as options in the dialog
                    .setItems(playlistNamesWithoutAllSongsArray) {_, which ->
                        // on item selection, delete the selected playlist from the "playlists" map
                        // and change the current Playlist to the "All Songs" playlist.
                        val selectedPlaylistName = playlistNamesWithoutAllSongsArray[which]
                        // remove the selected playlist from the "playlists" map
                        playlists.remove(selectedPlaylistName)
                        val deletedCurrentPlaylist = currentPlaylistName == selectedPlaylistName
                        val deletedDisplayedPlaylist = displayedPlaylistName == selectedPlaylistName
                        // delete the selected playlist from the database using the database helper
                        databaseHelper.deletePlaylist(selectedPlaylistName)

                        // if the playlist is deleted on the ADD_PLAYLIST state,
                        // then display the "All Songs" playlist
                        // get the "all songs" list
                        val allSongs = ensureAllSongsLoaded() // get the "All Songs" playlist using ensureAllSongsLoaded to make sure it's loaded and up to date
                        if (deletedCurrentPlaylist) {
                            updateCurrentPlaylist("All Songs", allSongs)
                        }

                        if (currentState == AppState.ADD_PLAYLIST) {
                            // Only replace the selector list if the deleted playlist is the one
                            // currently shown in ADD_PLAYLIST. If another playlist was deleted,
                            // the visible selector list must remain unchanged.
                            if (deletedDisplayedPlaylist) {
                                updateDisplayedPlaylist("All Songs", allSongs)
                            }
                            renderSongList(resetScrollToTop = true)
                        } else {
                            // If the deleted playlist was not active on PLAY_MUSIC, keep the
                            // current list as-is. Otherwise, switch back to All Songs.
                            if (deletedCurrentPlaylist) {
                                updateCurrentPlaylist("All Songs", allSongs)
                            }
                            renderSongList()
                        }
                        refreshControllerUi()
                    }
                    .setNegativeButton("cancel", null)
                    .show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // Function that handles the transition between states
    // responsible for updating the RecyclerView adapter and swapping
    // the fragments in the mainFragmentContainer
    fun transitionToState(newState: AppState){
        // if the current state is the same as the new state, we don't need to update the current state variable
        if (currentState == newState){
            return
        }
        // transition to the new state by updating the RecyclerView adapter and swapping fragments
        val transaction = supportFragmentManager.beginTransaction()
        // use when expression to determine which fragment to show based on the new state
        when (newState) { // set fragment and adapter mode based on the new state
            AppState.PLAY_MUSIC -> {
                // Update Fragment Container to show the MainPlayerControllerFragment
                transaction.replace(R.id.mainFragmentContainer, MainPlayerControllerFragment())
            }
            AppState.ADD_PLAYLIST -> {
                // Update Fragment Container to show the AddPlaylistFragment
                transaction.replace(R.id.mainFragmentContainer, MainAddPlaylistFragment())
            }
        }
        transaction.commit() // Commit the fragment transaction to apply the changes
        supportFragmentManager.executePendingTransactions()
        currentState = newState // Update the current state variable to the new state after the transition is complete
        renderSongList(resetScrollToTop = newState == AppState.ADD_PLAYLIST)
        refreshControllerUi()
    }

    // method to handle play pause click events
    fun handlePlayPauseClick(song: Song){
        // if no current track make the clicked song the current one and playing
        if (currentTrack == null){
            currentTrack = Track(song, TrackState.STOPPED)
            recreateMediaPlayer()
            currentTrack!!.state = if (tryStartPlayback()) TrackState.PLAYING else TrackState.STOPPED
        } else if (currentTrack?.song?.id != song.id) { // if the clicked song is different from the current track.
            // pause the old track
            currentTrack!!.state = TrackState.STOPPED
            // replace the current track with the new one and set it to playing
            currentTrack = Track(song, TrackState.STOPPED)
            recreateMediaPlayer()
            currentTrack!!.state = if (tryStartPlayback()) TrackState.PLAYING else TrackState.STOPPED
        } else {
            // if the clicked song is the same as the current track, toggle between play and pause
            currentTrack!!.state = when (currentTrack!!.state) {
                TrackState.PLAYING -> {
                    mediaPlayer?.pause()
                    TrackState.PAUSED
                }
                TrackState.PAUSED, TrackState.STOPPED -> {
                    if (tryStartPlayback()) TrackState.PLAYING else TrackState.STOPPED
                }
            }
        }
        // before refreshing the adapter, push the updated currentTrack into the adapter
        renderSongList()

        refreshControllerUi()
    }
    // method to handle long click events
    fun handleSongLongClick(song: Song){
        if (currentState == AppState.ADD_PLAYLIST) return
        // get all songs via a variable
        val allSongs = playlists["All Songs"].orEmpty()
        // load playlist selectors with build selectors
        loadedPlaylistSelectors = buildSelectors(allSongs)
        displayedPlaylistName = "All Songs"
        // transition to the ADD_PLAYLIST state to show the selectors
        loadedPlaylistSelectors.forEach { it.isSelected = it.song.id == song.id }
        // transition to the ADD_PLAYLIST state to show the selectors
        transitionToState(AppState.ADD_PLAYLIST)
    }
    // helper function to add a playlist with a given name and list of songs
    // this function can be called by the MainAddPlaylistFragment when the user clicks the "OK" button to create a new playlist
    fun addPlaylist(playlistName: String, songs: List<Song>) {
        // save the playlist to the database first
        databaseHelper.replacePlaylist(playlistName, songs)
        // then update playlists[playlistName] in memory
        playlists[playlistName] = songs.toMutableList()
        // return to PLAY_MUSIC state after adding the playlist
        transitionToState(AppState.PLAY_MUSIC)
    }
    // create a helper that ensures "All Songs" playlist is loaded
    fun ensureAllSongsLoaded(): List<Song> {
        // Check if the "All Songs" playlist exists and is not empty
        // if yes, just return the "All Songs" playlist from the map
        val allSongs = playlists["All Songs"]
        if (!allSongs.isNullOrEmpty()) {
            return allSongs // return the "All Songs" playlist from the map if it exists and is not empty
        } else {
            // if not, call the storage query method and save results in the "All Songs" playlist in the map, then return the loaded songs
            val songs = fetchSongsFromStorage()
            playlists["All Songs"] = songs.toMutableList()
            return songs
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

}
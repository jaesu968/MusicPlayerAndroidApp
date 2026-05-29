package org.hyperskill.musicplayer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast


class MainAddPlaylistFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Called when the Fragment is created (before its UI is drawn).
        super.onCreate(savedInstanceState)

    }
    // Called to create the Fragment's UI. We load the layout from fragment_main_add_playlist.xml.
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Create the Fragment UI from fragment_main_add_playlist.xml.
        return inflater.inflate(R.layout.fragment_main_add_playlist, container, false)
    }
    // override onViewCreated to set up the UI elements and interactions after the view is created
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        // find the EditText for the playlist name and set up a click listener for the save button
        val playlistNameET = view.findViewById<EditText>(R.id.addPlaylistEtPlaylistName)
        // find the ok button
        val okBtn = view.findViewById<Button>(R.id.addPlaylistBtnOk)
        // find the cancel button
        val cancelBtn = view.findViewById<Button>(R.id.addPlaylistBtnCancel)

        // set up click listener for the cancel button to just transition back to PLAY_MUSIC state
        cancelBtn.setOnClickListener {
            // use transitionToState from main activity
            (activity as MainActivity).transitionToState(AppState.PLAY_MUSIC)
            // current playlist should already be unchanged since
            // we haven't made any changes to it in the ADD_PLAYLIST state,
            // so we don't need to do anything else here
        }
        // set up click listener for the ok button
        okBtn.setOnClickListener {
            // get the main activity so we can call functions needed to update / save playlists
            val mainActivity = activity as MainActivity
            // get the selected songs from loadedPlaylistSelectors in the main activity
            val selectedSongs = mainActivity.loadedPlaylistSelectors
                .filter { it.isSelected } // filter the list to only include selected songs
                .map { it.song } // map the list of SongSelector to a list of Song
            // if not selected songs, show a toast message and return
            if(selectedSongs.isEmpty()){
                Toast.makeText(requireContext(), "Add at least one song to your playlist", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // read and trim the playlist name from the EditText
            val playlistName = playlistNameET.text.toString().trim()
            // check if the playlist name is blank
            if(playlistName.isBlank()){
                // If the playlist name is blank, then show a toast message and return
                Toast.makeText(requireContext(), "Add a name to your playlist", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // if there are selected songs but text for addPlaylistEtPlaylistName is blank, show a toast message
            if(playlistName == "All Songs"){
                Toast.makeText(requireContext(), "All Songs is a reserved name choose another playlist name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Otherwise add a playlist name taken from addPlayListEtPlaylistName text
            // and with the songs from the list of selected SongSelectors and transition back to PLAY_MUSIC state
            mainActivity.addPlaylist(playlistName, selectedSongs)
        }
    }
}
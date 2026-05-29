package org.hyperskill.musicplayer

import android.annotation.SuppressLint
import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import java.util.Locale

class SongAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // enum class for the two modes: VIEW and SELECT.
    enum class SongListMode {
        VIEW, SELECT
    }
    // add constants for the view types
    private companion object {
        const val TYPE_VIEW = 0 // for the normal song item layout
        const val TYPE_SELECT = 1 // for the song item layout with a checkbox for selection
    }
    var currentMode: SongListMode = SongListMode.VIEW // Default to VIEW mode, this will be updated from the MainActivity when the user switches between modes
    var currentTrack: Track? = null // this will be set from the MainActivity to keep track of the currently playing track and its state
    // create variables for songs and selectors
    private val songs = mutableListOf<Song>()
    // list of songs to display in the adapter,
    val selectors = mutableListOf<SongSelector>() // list of SongSelector objects to
    // constructor callbacks for onPlayPauseClick and onSongLongClick to handle play/pause and long click events in the view holder
    var onPlayPauseClick: ((Song) -> Unit)? = null
    var onSongLongClick: ((Song) -> Unit)? = null
    // add a selector toggle click callback for the SongSelectorViewHolder to handle selection events in the select mode
    var onSelectorToggleClick: ((SongSelector) -> Unit)? = null

    // create a function to update songs
    fun submitSongs(newSongs: List<Song>) {
        replaceItems(songs, newSongs)
    }
    // create a function to update selectors
    fun submitSelectors(newSelectors: List<SongSelector>) {
        replaceItems(selectors, newSelectors)
    }

    private fun <T> replaceItems(target: MutableList<T>, source: List<T>) {
        target.clear()
        target.addAll(source)
        notifyDataSetChanged()
    }
    // getItemViewType(position) returns based on current mode
        override fun getItemViewType(position: Int): Int =
         if (currentMode == SongListMode.VIEW) TYPE_VIEW else TYPE_SELECT
    // override the getItemCount function to return the number of songs in the list
    override fun getItemCount(): Int =
        if (currentMode == SongListMode.VIEW) songs.size else selectors.size
    // override fun onCreateViewHolder(...) { ... }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater: LayoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_VIEW) {
            // inflate the list_item_song layout and return a SongViewHolder
            SongViewHolder(inflater.inflate(R.layout.list_item_song, parent, false))
        } else {
            // inflate the list_item_song_selector layout and return a SongSelectorViewHolder
            SongSelectorViewHolder(inflater.inflate(R.layout.list_item_song_selector, parent, false))
        }
    }
    // override fun onBindViewHolder to bind the song data to the view holder
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // bind the song data if the holder is a SongViewHolder, or bind the selector data if it's a SongSelectorViewHolder
        if (holder is SongViewHolder) {
            val song = songs[position]
            holder.bind(song) // bind the song data to the views in the holder
            // set click listeners for the play/pause button and the whole item view to handle play/pause and long click events
            holder.playPauseBtn.setOnClickListener { onPlayPauseClick?.invoke(song) } // invoke the play/pause click callback with the song when the button is clicked
            holder.itemView.setOnLongClickListener {
                onSongLongClick?.invoke(song) // invoke the long click callback with the song when the item view is clicked
                true
            }
        } else if (holder is SongSelectorViewHolder){
            val selector = selectors[position] // bind the selector data to the views in the holder (e.g., set the checkbox state and song title)
            holder.bind(selector)
            holder.itemView.setOnClickListener {
                selector.isSelected = !selector.isSelected
                holder.bind(selector)
                onSelectorToggleClick?.invoke(selector) // invoke the selector toggle click callback with the selector when the checkbox state changes
            }

        }
    }


    // define SongViewHolder and SongSelectorViewHolder as inner classes of the adapter
    // add two inner holder classes for the two view types: SongViewHolder and SongSelectorViewHolder
    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView){
        // add fields for the view in the list_item_song layout:
        val playPauseBtn: ImageView = itemView.findViewById(R.id.songItemImgBtnPlayPause)
        val artistTv: TextView = itemView.findViewById(R.id.songItemTvArtist)
        val titleTv: TextView = itemView.findViewById(R.id.songItemTvTitle)
        val durationTv: TextView = itemView.findViewById(R.id.songItemTvDuration)
        // add a bind function to bind the song data to the views in the holder
        fun bind(song: Song){
            artistTv.text = song.artist
            titleTv.text = song.title
            // format duration from milliseconds to minutes:seconds
            durationTv.text = formatDuration(song.duration)
            // set songItemImgBtnPlayPause image based on if the song is the current track and its state
            // if the row is the current playing track, set to pause icon for all rows, otherwise set to play icon
            if(currentTrack != null && currentTrack!!.song.id == song.id) {
                // this is the current track: set the play/pause button based on the track state
                when (currentTrack!!.state) {
                    TrackState.PLAYING -> playPauseBtn.setImageResource(R.drawable.ic_pause) // set
                    TrackState.PAUSED, TrackState.STOPPED -> playPauseBtn.setImageResource(R.drawable.ic_play)
                }
            } else {
                // this is not the current track: set to play icon
                playPauseBtn.setImageResource(R.drawable.ic_play)
            }
        }
        // helper function
        @SuppressLint("DefaultLocale")
        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            // use Locale aware formatting to ensure the seconds are always displayed with two digits
            return String.format("%02d:%02d", minutes, seconds)
        }
    }
    class SongSelectorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // add fields for the views in the list_item_song_selector layout:
        val checkBox: CheckBox = itemView.findViewById(R.id.songSelectorItemCheckBox)
        private val artistTv: TextView = itemView.findViewById(R.id.songSelectorItemTvArtist)
        private val titleTv: TextView = itemView.findViewById(R.id.songSelectorItemTvTitle)
        private val durationTv: TextView = itemView.findViewById(R.id.songSelectorItemTvDuration)

        fun bind(selector: SongSelector) {
            artistTv.text = selector.song.artist // set the artist name in the TextView
            titleTv.text = selector.song.title // set the song title in the TextView
            durationTv.text = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                selector.song.duration / 60000,
                (selector.song.duration % 60000) / 1000
            ) // set the formatted duration in the Text
            checkBox.isChecked = selector.isSelected // set the checkbox state based on the isSelected property of the SongSelector
            itemView.setBackgroundColor(if (selector.isSelected) Color.LTGRAY else Color.WHITE)
        }
    }


}
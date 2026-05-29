package org.hyperskill.musicplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

class MainPlayerControllerFragment : Fragment() {

    // NOTE: MediaPlayer is intentionally NOT stored here. It lives in MainActivity so it
    // survives fragment replacements (e.g. PLAY_MUSIC -> ADD_PLAYLIST -> PLAY_MUSIC).
    // We access it via (activity as MainActivity).mediaPlayer whenever we need it.

    // Handler lets us schedule code to run on the main (UI) thread after a delay.
    private val handler = Handler(Looper.getMainLooper())

    // While the user is dragging the SeekBar we temporarily stop automatic updates.
    private var isTracking = false

    // Class-level references so the update loop can reach the widgets.
    private var seekBar: SeekBar? = null
    private var currentTimeView: TextView? = null
    private var totalTimeView: TextView? = null

    // Recursive update loop: every 200 ms read MediaPlayer.currentPosition and push it
    // into the controller widgets.
    private val updateRunnable = object : Runnable {
        override fun run() {
            val mainActivity = activity as? MainActivity ?: return
            val mp = mainActivity.mediaPlayer

            syncDurationUi(mainActivity)

            if (mp != null && !isTracking) {
                try {
                    val currentPos = mp.currentPosition
                    seekBar?.progress = currentPos / 1000
                    currentTimeView?.text = formatTime(currentPos.toLong())
                } catch (_: IllegalStateException) {
                    // If the player is being recreated, just skip this tick.
                }
            }

            handler.postDelayed(this, 200)
        }
    }

    fun startUpdate() {
        handler.removeCallbacks(updateRunnable)
        handler.postDelayed(updateRunnable, 200)
    }

    fun stopUpdate() {
        handler.removeCallbacks(updateRunnable)
        syncProgress()
    }

    // Force an immediate UI refresh without waiting for the next 200 ms tick.
    // This is useful after Search or after loading another playlist, because the
    // selected track/duration may change even when playback is stopped.
    fun refreshController() {
        val mainActivity = activity as? MainActivity ?: return
        syncDurationUi(mainActivity)
        syncProgress()
    }

    fun syncProgress() {
        val mainActivity = activity as? MainActivity ?: return
        val mp = mainActivity.mediaPlayer ?: return
        if (!isTracking) {
            try {
                val currentPos = if (mainActivity.currentTrack?.state == TrackState.STOPPED) 0 else mp.currentPosition
                seekBar?.progress = currentPos / 1000
                currentTimeView?.text = formatTime(currentPos.toLong())
            } catch (_: IllegalStateException) {
            }
        }
    }

    private fun scheduleUpdate() = startUpdate()

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    // Total duration is based on the selected Song metadata. Before Search/currentTrack,
    // the controller should stay at 00:00.
    private fun syncDurationUi(mainActivity: MainActivity) {
        val durationMs = mainActivity.currentTrack?.song?.duration ?: 0L
        seekBar?.max = (durationMs / 1000).toInt()
        totalTimeView?.text = formatTime(durationMs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_player_controller, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as MainActivity

        seekBar = view.findViewById(R.id.controllerSeekBar)
        currentTimeView = view.findViewById(R.id.controllerTvCurrentTime)
        totalTimeView = view.findViewById(R.id.controllerTvTotalTime)
        val playPauseBtn = view.findViewById<Button>(R.id.controllerBtnPlayPause)
        val stopBtn = view.findViewById<Button>(R.id.controllerBtnStop)

        syncDurationUi(mainActivity)
        syncProgress()

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) {
                isTracking = true
                handler.removeCallbacks(updateRunnable)
            }

            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTimeView?.text = formatTime(progress * 1000L)
                }
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                mainActivity.mediaPlayer?.setOnSeekCompleteListener {
                    isTracking = false
                    scheduleUpdate()
                    mainActivity.mediaPlayer?.setOnSeekCompleteListener(null)
                }
                mainActivity.mediaPlayer?.seekTo(sb.progress * 1000)
                handler.postDelayed({
                    if (isTracking) {
                        isTracking = false
                        scheduleUpdate()
                    }
                }, 350)
            }
        })

        playPauseBtn.setOnClickListener {
            val track = mainActivity.currentTrack ?: return@setOnClickListener
            mainActivity.handlePlayPauseClick(track.song)
            if (mainActivity.currentTrack?.state == TrackState.PLAYING) {
                scheduleUpdate()
            }
            mainActivity.songAdapter.submitSongs(mainActivity.currentPlaylist)
        }

        stopBtn.setOnClickListener {
            mainActivity.currentTrack?.let { track ->
                track.state = TrackState.STOPPED
                mainActivity.mediaPlayer?.pause()
                mainActivity.mediaPlayer?.seekTo(0)
                seekBar?.progress = 0
                currentTimeView?.text = formatTime(0L)
                mainActivity.songAdapter.currentTrack = mainActivity.currentTrack
                mainActivity.songAdapter.submitSongs(mainActivity.currentPlaylist)
                stopUpdate()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onResume() {
        super.onResume()
        scheduleUpdate()
    }
}
package org.hyperskill.musicplayer

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

class MusicPlayerDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    // override onConfigure to enable foreign key constraints
    override fun onConfigure(db: SQLiteDatabase) {
        // call super onConfigure to ensure proper configuration of the database
        super.onConfigure(db)
        db.execSQL("PRAGMA foreign_keys = 1")
        db.execSQL("PRAGMA trusted_schema = 0")
    }
    // override onCreate to create the playlist table
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS playlist (" +
                    "playlistName TEXT," +
                    "songId INTEGER," +
                    "PRIMARY KEY (playlistName, songId)"
                    + ")"
            )
    }
    // override onUpgrade to drop the playlist table and call onCreate to recreate it
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw UnsupportedOperationException("Database upgrade not supported")
    }
    // companion object to hold the singleton instance of the database helper
    companion object {
        private const val DATABASE_NAME = "musicPlayerDatabase.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_PLAYLIST = "playlist"
        private const val COLUMN_PLAYLIST_NAME = "playlistName"
        private const val COLUMN_SONG_ID = "songId"
        // define ALL_SONG constant to represent all songs in the playlist table
        const val ALL_SONGS = "All Songs"
    }
    // method to replace a playlist
    fun replacePlaylist(playlistName: String, songs: List<Song>){
        // delete all songs in the playlist with the given name
        // check if it is the "All Songs" playlist first, and if so, do nothing since we don't want to delete all songs from the database
        if(playlistName == ALL_SONGS) return
        // get writable database instance
        val db = writableDatabase
        db.transaction {
            // delete all songs in the playlist with the given name
            db.delete(TABLE_PLAYLIST, "$COLUMN_PLAYLIST_NAME = ?", arrayOf(playlistName))
            // before song insertion ensure no duplicates are present in the playlist by using a set to track seen song IDs
            val uniqueSongs = songs.distinctBy { it.id } // create a list of unique songs based on their IDs
            // insert the new songs into the playlist
            for (song in uniqueSongs) {
                val values = ContentValues().apply {
                    put(COLUMN_PLAYLIST_NAME, playlistName)
                    put(COLUMN_SONG_ID, song.id)
                }
                db.insert(TABLE_PLAYLIST, null, values)
            }
        }
    }
    // method to delete a playlist
    fun deletePlaylist(playlistName: String){
        // If the playlist is the "All Songs" playlist, then do nothing since we don't want to delete all songs from the database
        if(playlistName == ALL_SONGS) return
        // get writable database
        val db = writableDatabase
        // delete all songs in the playlist with the given name
        db.delete(TABLE_PLAYLIST, "$COLUMN_PLAYLIST_NAME = ?", arrayOf(playlistName))
    }
    // method get all saved playlist names
    fun getSavedPlaylistNames(): List<String>{
        // return all distinct playlist names from the table
        val db = readableDatabase // get a readable database instance
        val playlistNames = mutableListOf<String>()
        db.rawQuery(
            "SELECT $COLUMN_PLAYLIST_NAME FROM $TABLE_PLAYLIST GROUP BY $COLUMN_PLAYLIST_NAME ORDER BY MIN(rowid) ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                playlistNames.add(cursor.getString(0))
            }
        }
        return playlistNames
    }
    // method to get song ids for a playlist
    fun getSongIdsForPlaylist(playlistName: String): List<Long> {
        // query all songId values for the playlist with the given name
        val db = readableDatabase // get a readable database instance
        val songIds = mutableListOf<Long>()
        db.query(
            TABLE_PLAYLIST,
            arrayOf(COLUMN_SONG_ID),
            "$COLUMN_PLAYLIST_NAME = ?",
            arrayOf(playlistName),
            null,
            null,
            "rowid ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                songIds.add(cursor.getLong(0))
            }
        }
        return songIds
    }

}



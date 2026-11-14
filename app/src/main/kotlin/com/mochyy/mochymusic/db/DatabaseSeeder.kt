package com.mochyy.mochymusic.db

import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

/**
 * Non-destructive first-launch starter seeder for `mochy.db` (v42).
 * Seeds curated starter playlists and SpeedDial items when the database is completely empty
 * so the Home SpeedDial, Library Playlists, and CrossMix test flows are immediately populated.
 */
object DatabaseSeeder {

    fun seedIfEmpty(db: SupportSQLiteDatabase) {
        try {
            val playlistCount = db.query("SELECT COUNT(*) FROM playlist").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            val speedDialCount = db.query("SELECT COUNT(*) FROM speed_dial_item").use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

            val nowMs = System.currentTimeMillis()

            if (playlistCount == 0) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO playlist (id, name, browseId, createdAt, lastUpdateTime, isEditable, bookmarkedAt, remoteSongCount, isLocal, isAutoSync, description)
                    VALUES
                    ('LP_CROSSMIX_DEMO', 'Audiophile Reference & CrossMix Demo', NULL, $nowMs, $nowMs, 1, $nowMs, 0, 1, 0, 'Curated reference tracks for testing AudioLab EQ, Parametric presets, and 1s–12s Smart CrossMix transitions.'),
                    ('LP_BEDSIDE_LOFI', 'Late Night Bedside & Lo-Fi', NULL, $nowMs, $nowMs, 1, $nowMs, 0, 1, 0, 'Smooth instrumental and chillhop sessions designed for the Bedside clock & TurnedSheets player mode.')
                    """.trimIndent()
                )
            }

            if (speedDialCount == 0) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO speed_dial_item (id, secondaryId, title, subtitle, thumbnailUrl, type, explicit, createDate)
                    VALUES
                    ('jfKfPfyJRdk', NULL, 'lofi hip hop radio - beats to relax/study to', 'Lofi Girl', 'https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg', 'SONG', 0, $nowMs),
                    ('4xDzrJKXOOY', NULL, 'synthwave radio - chill synth / retro beats', 'Lofi Girl', 'https://i.ytimg.com/vi/4xDzrJKXOOY/hqdefault.jpg', 'SONG', 0, ${nowMs - 1000}),
                    ('5qap5aO4i9A', NULL, 'Chillhop Essentials', 'Chillhop Music', 'https://i.ytimg.com/vi/5qap5aO4i9A/hqdefault.jpg', 'SONG', 0, ${nowMs - 2000})
                    """.trimIndent()
                )
            }
        } catch (e: Exception) {
            Timber.tag("DatabaseSeeder").w(e, "Skipped non-destructive starter seed")
        }
    }
}

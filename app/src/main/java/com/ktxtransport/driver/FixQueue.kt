package com.ktxtransport.driver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Positions waiting to be posted, oldest first. Every fix goes through here so a
 * dead zone, a server error or the app being killed never loses one.
 */
class FixQueue private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "fixes.db", null, 1) {

    data class Fix(val id: Long, val url: String, val body: String)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE fix (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, body TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(url: String, body: String) {
        val db = writableDatabase
        db.insert("fix", null, ContentValues().apply {
            put("url", url)
            put("body", body)
        })
        // About a week of 60-second fixes; beyond that drop the oldest rather than grow forever.
        db.execSQL("DELETE FROM fix WHERE id <= (SELECT MAX(id) FROM fix) - $MAX_ROWS")
    }

    fun oldest(limit: Int): List<Fix> =
        readableDatabase.rawQuery("SELECT id, url, body FROM fix ORDER BY id LIMIT $limit", null).use { c ->
            buildList {
                while (c.moveToNext()) add(Fix(c.getLong(0), c.getString(1), c.getString(2)))
            }
        }

    fun remove(id: Long) {
        writableDatabase.delete("fix", "id = ?", arrayOf(id.toString()))
    }

    fun count(): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM fix", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    companion object {
        private const val MAX_ROWS = 10_000

        @Volatile
        private var instance: FixQueue? = null

        fun get(context: Context): FixQueue =
            instance ?: synchronized(this) { instance ?: FixQueue(context).also { instance = it } }
    }
}

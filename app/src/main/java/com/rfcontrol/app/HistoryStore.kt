package com.rfcontrol.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Detection(val session: Long, val number: Int, val kind: String, val identifier: String,
    val name: String, val details: String, val first: Long, val last: Long, val rssi: Int, val samples: Int)

/** One durable row per identifier per scan; repeated BLE advertisements update last seen and sample count. */
class HistoryStore(context: Context) : SQLiteOpenHelper(context, "detections.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, started INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE detections (session INTEGER NOT NULL, number INTEGER NOT NULL, kind TEXT NOT NULL, identifier TEXT NOT NULL, name TEXT NOT NULL, details TEXT NOT NULL, first INTEGER NOT NULL, last INTEGER NOT NULL, rssi INTEGER NOT NULL, samples INTEGER NOT NULL, PRIMARY KEY(session, identifier))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun begin(kind: String, time: Long = System.currentTimeMillis()): Long = writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
        put("kind", kind); put("started", time)
    })
    fun record(session: Long, kind: String, identifier: String, name: String, details: String, time: Long, rssi: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val old = db.query("detections", null, "session=? AND identifier=?", arrayOf(session.toString(), identifier), null, null, null).use { c ->
                if (!c.moveToFirst()) null else Detection(session,
                    c.getInt(c.getColumnIndexOrThrow("number")), kind, identifier,
                    c.getString(c.getColumnIndexOrThrow("name")), details,
                    c.getLong(c.getColumnIndexOrThrow("first")), c.getLong(c.getColumnIndexOrThrow("last")),
                    rssi, c.getInt(c.getColumnIndexOrThrow("samples")))
            }
            val number = old?.number ?: db.rawQuery("SELECT COALESCE(MAX(number), 0) + 1 FROM detections WHERE session=?", arrayOf(session.toString())).use { c -> c.moveToFirst(); c.getInt(0) }
            val values = ContentValues().apply {
                put("session", session); put("number", number)
                put("kind", kind); put("identifier", identifier)
                put("name", name.ifBlank { old?.name ?: "Name unavailable" })
                put("details", details); put("first", old?.first ?: time); put("last", maxOf(old?.last ?: time, time))
                put("rssi", rssi); put("samples", (old?.samples ?: 0) + 1)
            }
            db.insertWithOnConflict("detections", null, values, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun rows(session: Long? = null, limit: Int = 500): List<Detection> {
        val result = mutableListOf<Detection>()
        readableDatabase.query("detections", null, if (session == null) null else "session=?",
            session?.let { arrayOf(it.toString()) }, null, null,
            if (session == null) "session DESC, number ASC" else "number ASC", limit.toString()).use { c ->
            while (c.moveToNext()) {
                fun s(key: String) = c.getString(c.getColumnIndexOrThrow(key))
                fun l(key: String) = c.getLong(c.getColumnIndexOrThrow(key))
                result += Detection(l("session"), l("number").toInt(), s("kind"), s("identifier"), s("name"), s("details"), l("first"), l("last"), l("rssi").toInt(), l("samples").toInt())
            }
        }
        return result
    }
    fun clear() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("detections", null, null)
            writableDatabase.delete("sessions", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
}

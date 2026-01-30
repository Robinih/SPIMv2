package com.cvsuagritech.spim.database

import android.content.ContentValues
import android.content.Context
import android.database.CursorWindow
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.cvsuagritech.spim.models.HistoryItem
import com.cvsuagritech.spim.models.PestRecord
import java.io.ByteArrayOutputStream
import java.lang.reflect.Field

class PestDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    init {
        increaseCursorWindowSize()
    }

    private fun increaseCursorWindowSize() {
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 50 * 1024 * 1024) // 50MB
        } catch (e: Exception) {
            Log.e("PestDatabaseHelper", "Failed to increase CursorWindow size", e)
        }
    }

    companion object {
        private const val DATABASE_NAME = "pest_database.db"
        private const val DATABASE_VERSION = 4 
        
        private const val TABLE_PEST_RECORDS = "pest_records"
        private const val TABLE_COUNT_RECORDS = "count_records"
        
        private const val COLUMN_ID = "id"
        private const val COLUMN_IMAGE_PATH = "image_path"
        private const val COLUMN_IMAGE_BLOB = "image_blob"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_NOTES = "notes"
        private const val COLUMN_IS_SYNCED = "is_synced"

        private const val COLUMN_PEST_NAME = "pest_name"
        private const val COLUMN_CONFIDENCE = "confidence"

        private const val COLUMN_TOTAL_COUNT = "total_count"
        private const val COLUMN_BREAKDOWN = "breakdown"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createPestTable = """
            CREATE TABLE $TABLE_PEST_RECORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PEST_NAME TEXT NOT NULL,
                $COLUMN_CONFIDENCE REAL NOT NULL,
                $COLUMN_IMAGE_PATH TEXT,
                $COLUMN_IMAGE_BLOB BLOB,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_NOTES TEXT,
                $COLUMN_IS_SYNCED INTEGER DEFAULT 0
            )
        """.trimIndent()
        
        val createCountTable = """
            CREATE TABLE $TABLE_COUNT_RECORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TOTAL_COUNT INTEGER NOT NULL,
                $COLUMN_BREAKDOWN TEXT,
                $COLUMN_IMAGE_PATH TEXT,
                $COLUMN_IMAGE_BLOB BLOB,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_NOTES TEXT,
                $COLUMN_IS_SYNCED INTEGER DEFAULT 0
            )
        """.trimIndent()
        
        db.execSQL(createPestTable)
        db.execSQL(createCountTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_COUNT_RECORDS ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_TOTAL_COUNT INTEGER NOT NULL, $COLUMN_IMAGE_PATH TEXT, $COLUMN_IMAGE_BLOB BLOB, $COLUMN_TIMESTAMP INTEGER NOT NULL, $COLUMN_NOTES TEXT)")
        }
        if (oldVersion < 3) {
            try { db.execSQL("ALTER TABLE $TABLE_COUNT_RECORDS ADD COLUMN $COLUMN_BREAKDOWN TEXT") } catch(e:Exception){}
        }
        if (oldVersion < 4) {
            try { db.execSQL("ALTER TABLE $TABLE_PEST_RECORDS ADD COLUMN $COLUMN_IS_SYNCED INTEGER DEFAULT 0") } catch(e:Exception){}
            try { db.execSQL("ALTER TABLE $TABLE_COUNT_RECORDS ADD COLUMN $COLUMN_IS_SYNCED INTEGER DEFAULT 0") } catch(e:Exception){}
        }
    }

    fun insertPestRecord(record: PestRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PEST_NAME, record.pestName)
            put(COLUMN_CONFIDENCE, record.confidence)
            put(COLUMN_IMAGE_PATH, record.imagePath)
            put(COLUMN_IMAGE_BLOB, record.imageBlob)
            put(COLUMN_TIMESTAMP, record.timestamp)
            put(COLUMN_NOTES, record.notes)
            put(COLUMN_IS_SYNCED, if (record.isSynced) 1 else 0)
        }
        return db.insert(TABLE_PEST_RECORDS, null, values)
    }

    fun insertCountRecord(item: HistoryItem.CountItem): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TOTAL_COUNT, item.totalCount)
            put(COLUMN_BREAKDOWN, item.breakdown)
            put(COLUMN_IMAGE_PATH, item.imagePath)
            put(COLUMN_IMAGE_BLOB, item.imageBlob)
            put(COLUMN_TIMESTAMP, item.timestamp)
            put(COLUMN_IS_SYNCED, if (item.isSynced) 1 else 0)
        }
        return db.insert(TABLE_COUNT_RECORDS, null, values)
    }

    fun markPestRecordSynced(id: Long) {
        val db = writableDatabase
        val values = ContentValues().apply { put(COLUMN_IS_SYNCED, 1) }
        db.update(TABLE_PEST_RECORDS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun markCountRecordSynced(id: Long) {
        val db = writableDatabase
        val values = ContentValues().apply { put(COLUMN_IS_SYNCED, 1) }
        db.update(TABLE_COUNT_RECORDS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun getUnsyncedPestRecords(): List<PestRecord> {
        val records = mutableListOf<PestRecord>()
        val db = readableDatabase
        val cursor = db.query(TABLE_PEST_RECORDS, null, "$COLUMN_IS_SYNCED = 0", null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                records.add(PestRecord(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    pestName = it.getString(it.getColumnIndexOrThrow(COLUMN_PEST_NAME)),
                    confidence = it.getFloat(it.getColumnIndexOrThrow(COLUMN_CONFIDENCE)),
                    imageBlob = it.getBlob(it.getColumnIndexOrThrow(COLUMN_IMAGE_BLOB)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    isSynced = false
                ))
            }
        }
        return records
    }

    fun getUnsyncedCountRecords(): List<HistoryItem.CountItem> {
        val records = mutableListOf<HistoryItem.CountItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_COUNT_RECORDS, null, "$COLUMN_IS_SYNCED = 0", null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                records.add(HistoryItem.CountItem(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    totalCount = it.getInt(it.getColumnIndexOrThrow(COLUMN_TOTAL_COUNT)),
                    breakdown = it.getString(it.getColumnIndexOrThrow(COLUMN_BREAKDOWN)),
                    imageBlob = it.getBlob(it.getColumnIndexOrThrow(COLUMN_IMAGE_BLOB)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    isSynced = false
                ))
            }
        }
        return records
    }

    fun getAllHistoryItems(): List<HistoryItem> {
        val items = mutableListOf<HistoryItem>()
        val db = readableDatabase

        val pestColumns = arrayOf(COLUMN_ID, COLUMN_PEST_NAME, COLUMN_CONFIDENCE, COLUMN_IMAGE_PATH, COLUMN_TIMESTAMP, COLUMN_IS_SYNCED)
        val idCursor = db.query(TABLE_PEST_RECORDS, pestColumns, null, null, null, null, "$COLUMN_TIMESTAMP DESC")
        idCursor.use {
            while (it.moveToNext()) {
                items.add(HistoryItem.IdentificationItem(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    insectName = it.getString(it.getColumnIndexOrThrow(COLUMN_PEST_NAME)),
                    confidence = it.getFloat(it.getColumnIndexOrThrow(COLUMN_CONFIDENCE)),
                    imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                    imageBlob = null, 
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    isSynced = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_SYNCED)) == 1
                ))
            }
        }

        val countColumns = arrayOf(COLUMN_ID, COLUMN_TOTAL_COUNT, COLUMN_BREAKDOWN, COLUMN_IMAGE_PATH, COLUMN_TIMESTAMP, COLUMN_IS_SYNCED)
        val countCursor = db.query(TABLE_COUNT_RECORDS, countColumns, null, null, null, null, "$COLUMN_TIMESTAMP DESC")
        countCursor.use {
            while (it.moveToNext()) {
                items.add(HistoryItem.CountItem(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    totalCount = it.getInt(it.getColumnIndexOrThrow(COLUMN_TOTAL_COUNT)),
                    breakdown = it.getString(it.getColumnIndexOrThrow(COLUMN_BREAKDOWN)),
                    imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                    imageBlob = null,
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    isSynced = it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_SYNCED)) == 1
                ))
            }
        }

        return items.sortedByDescending { it.timestamp }
    }

    fun getPestImage(id: Long): ByteArray? {
        val db = readableDatabase
        val cursor = db.query(TABLE_PEST_RECORDS, arrayOf(COLUMN_IMAGE_BLOB), "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) it.getBlob(0) else null
        }
    }

    fun getCountImage(id: Long): ByteArray? {
        val db = readableDatabase
        val cursor = db.query(TABLE_COUNT_RECORDS, arrayOf(COLUMN_IMAGE_BLOB), "$COLUMN_ID = ?", arrayOf(id.toString()), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) it.getBlob(0) else null
        }
    }

    fun deleteAllPestRecords(): Int {
        val db = writableDatabase
        val count = db.delete(TABLE_PEST_RECORDS, null, null)
        db.delete(TABLE_COUNT_RECORDS, null, null)
        return count
    }

    fun getTotalRecordsCount(): Int {
        val db = readableDatabase
        val c1 = db.rawQuery("SELECT COUNT(*) FROM ${TABLE_PEST_RECORDS}", null)
        val c2 = db.rawQuery("SELECT COUNT(*) FROM ${TABLE_COUNT_RECORDS}", null)
        var total = 0
        if (c1.moveToFirst()) total += c1.getInt(0)
        if (c2.moveToFirst()) total += c2.getInt(0)
        c1.close()
        c2.close()
        return total
    }
}

package com.cvsuagritech.spim.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cvsuagritech.spim.models.PestRecord
import java.io.ByteArrayOutputStream

class PestDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "pest_database.db"
        private const val DATABASE_VERSION = 1
        
        // Table name
        private const val TABLE_PEST_RECORDS = "pest_records"
        
        // Column names
        private const val COLUMN_ID = "id"
        private const val COLUMN_PEST_NAME = "pest_name"
        private const val COLUMN_CONFIDENCE = "confidence"
        private const val COLUMN_IMAGE_PATH = "image_path"
        private const val COLUMN_IMAGE_BLOB = "image_blob"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_NOTES = "notes"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_PEST_RECORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PEST_NAME TEXT NOT NULL,
                $COLUMN_CONFIDENCE REAL NOT NULL,
                $COLUMN_IMAGE_PATH TEXT,
                $COLUMN_IMAGE_BLOB BLOB,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_NOTES TEXT
            )
        """.trimIndent()
        
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PEST_RECORDS")
        onCreate(db)
    }

    fun insertPestRecord(pestRecord: PestRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PEST_NAME, pestRecord.pestName)
            put(COLUMN_CONFIDENCE, pestRecord.confidence)
            put(COLUMN_IMAGE_PATH, pestRecord.imagePath)
            put(COLUMN_IMAGE_BLOB, pestRecord.imageBlob)
            put(COLUMN_TIMESTAMP, pestRecord.timestamp)
            put(COLUMN_NOTES, pestRecord.notes)
        }
        
        return db.insert(TABLE_PEST_RECORDS, null, values)
    }

    fun getAllPestRecords(): List<PestRecord> {
        val records = mutableListOf<PestRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PEST_RECORDS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                val record = PestRecord(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    pestName = it.getString(it.getColumnIndexOrThrow(COLUMN_PEST_NAME)),
                    confidence = it.getFloat(it.getColumnIndexOrThrow(COLUMN_CONFIDENCE)),
                    imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                    imageBlob = it.getBlob(it.getColumnIndexOrThrow(COLUMN_IMAGE_BLOB)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    notes = it.getString(it.getColumnIndexOrThrow(COLUMN_NOTES))
                )
                records.add(record)
            }
        }

        return records
    }

    fun getPestRecordById(id: Long): PestRecord? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PEST_RECORDS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                return PestRecord(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    pestName = it.getString(it.getColumnIndexOrThrow(COLUMN_PEST_NAME)),
                    confidence = it.getFloat(it.getColumnIndexOrThrow(COLUMN_CONFIDENCE)),
                    imagePath = it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                    imageBlob = it.getBlob(it.getColumnIndexOrThrow(COLUMN_IMAGE_BLOB)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    notes = it.getString(it.getColumnIndexOrThrow(COLUMN_NOTES))
                )
            }
        }
        return null
    }

    fun deletePestRecord(id: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_PEST_RECORDS, "$COLUMN_ID = ?", arrayOf(id.toString())) > 0
    }

    fun deleteAllPestRecords(): Int {
        val db = writableDatabase
        return db.delete(TABLE_PEST_RECORDS, null, null)
    }

    fun getTotalRecordsCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_PEST_RECORDS", null)
        return if (cursor.moveToFirst()) {
            cursor.getInt(0)
        } else {
            0
        }.also { cursor.close() }
    }

    fun updatePestRecord(pestRecord: PestRecord): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PEST_NAME, pestRecord.pestName)
            put(COLUMN_CONFIDENCE, pestRecord.confidence)
            put(COLUMN_IMAGE_PATH, pestRecord.imagePath)
            put(COLUMN_IMAGE_BLOB, pestRecord.imageBlob)
            put(COLUMN_TIMESTAMP, pestRecord.timestamp)
            put(COLUMN_NOTES, pestRecord.notes)
        }
        
        return db.update(
            TABLE_PEST_RECORDS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(pestRecord.id.toString())
        ) > 0
    }

    // Helper function to convert Bitmap to ByteArray
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    // Helper function to convert ByteArray to Bitmap
    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        } catch (e: Exception) {
            null
        }
    }
}

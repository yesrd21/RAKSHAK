package com.rakshak.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rakshak.database.dao.CallLogDao
import com.rakshak.database.dao.FraudReportDao
import com.rakshak.database.dao.ScanStatsDao
import com.rakshak.database.dao.SmsLogDao
import com.rakshak.database.entities.CallLogEntry
import com.rakshak.database.entities.FraudReport
import com.rakshak.database.entities.ScanStats
import com.rakshak.database.entities.SmsLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rakshak Room Database
 * v1 → v2: Added call_log table
 * v2 → v3: Added sms_log table for message history feature
 */
@Database(
    entities = [FraudReport::class, ScanStats::class, CallLogEntry::class, SmsLogEntry::class],
    version = 3,
    exportSchema = false
)
abstract class RakshakDatabase : RoomDatabase() {

    abstract fun fraudReportDao(): FraudReportDao
    abstract fun scanStatsDao(): ScanStatsDao
    abstract fun callLogDao(): CallLogDao
    abstract fun smsLogDao(): SmsLogDao

    companion object {
        @Volatile
        private var INSTANCE: RakshakDatabase? = null

        /** Migration: v1 → v2 adds the call_log table. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS call_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        callerNumber TEXT NOT NULL,
                        riskScore INTEGER NOT NULL,
                        riskLevel TEXT NOT NULL,
                        signals TEXT NOT NULL,
                        isCorrelated INTEGER NOT NULL,
                        correlatedSmsScore INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /** Migration: v2 → v3 adds the sms_log table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sms_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sender TEXT NOT NULL,
                        riskScore INTEGER NOT NULL,
                        riskLevel TEXT NOT NULL,
                        detectedPatterns TEXT NOT NULL,
                        hasUrl INTEGER NOT NULL,
                        sequencePattern TEXT,
                        communityReportCount INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): RakshakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RakshakDatabase::class.java,
                    "rakshak_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        database.scanStatsDao().insertStats(ScanStats(id = 1))
                    }
                }
            }
        }
    }
}

package com.rakshak.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.rakshak.database.entities.SmsLogEntry

@Dao
interface SmsLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsLog(entry: SmsLogEntry)

    @Query("SELECT * FROM sms_log ORDER BY timestamp DESC LIMIT 500")
    fun getAllSmsLogs(): LiveData<List<SmsLogEntry>>

    @Query("SELECT * FROM sms_log WHERE riskLevel = :level ORDER BY timestamp DESC")
    fun getSmsLogsByLevel(level: String): LiveData<List<SmsLogEntry>>

    @Query("SELECT COUNT(*) FROM sms_log")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM sms_log")
    suspend fun clearAll()
}

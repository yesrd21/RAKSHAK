package com.rakshak.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.rakshak.database.entities.CallLogEntry

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(entry: CallLogEntry)

    @Query("SELECT * FROM call_log ORDER BY timestamp DESC LIMIT 100")
    fun getAllCallLogs(): LiveData<List<CallLogEntry>>

    @Query("SELECT COUNT(*) FROM call_log")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM call_log WHERE isCorrelated = 1")
    suspend fun getCorrelatedCount(): Int

    @Query("DELETE FROM call_log")
    suspend fun clearAll()
}

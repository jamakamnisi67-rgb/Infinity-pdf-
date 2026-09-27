package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfHistoryDao {
    @Query("SELECT * FROM pdf_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<PdfHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PdfHistoryItem): Long

    @Delete
    suspend fun deleteItem(item: PdfHistoryItem)

    @Query("DELETE FROM pdf_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pdf_history")
    suspend fun clearAll()
}

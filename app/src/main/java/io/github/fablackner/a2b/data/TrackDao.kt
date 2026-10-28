package io.github.fablackner.a2b.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE createdByMe = 1 ORDER BY name COLLATE NOCASE")
    fun observeMine(): Flow<List<Track>>

    @Query("SELECT * FROM tracks")
    suspend fun getAll(): List<Track>

    @Query("SELECT * FROM tracks WHERE stamp = :stamp")
    suspend fun findByStamp(stamp: Int): Track?

    @Upsert
    suspend fun upsert(track: Track)

    @Query("DELETE FROM tracks WHERE stamp = :stamp")
    suspend fun delete(stamp: Int)
}

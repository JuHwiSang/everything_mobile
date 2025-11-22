package com.example.everything_mobile.data.files

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.everything_mobile.data.files.FileEntity
import com.example.everything_mobile.data.files.ScanResult

@Dao
interface FileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Update
    suspend fun updateAll(files: List<FileEntity>)

    @Query("DELETE FROM files WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("SELECT * FROM files")
    suspend fun getAllFilesSync(): List<FileEntity>

    // [핵심] 이 메서드 하나로 트랜잭션 처리 끝
    @Transaction
    suspend fun applyDiff(result: ScanResult) {
        if (result.toInsert.isNotEmpty()) insertAll(result.toInsert)
        if (result.toUpdate.isNotEmpty()) updateAll(result.toUpdate)
        if (result.toDelete.isNotEmpty()) deleteByPaths(result.toDelete)
    }

    @Query("SELECT * FROM files ORDER BY lastModified DESC")
    suspend fun getAllFiles(): List<FileEntity>

    @Query("SELECT * FROM files WHERE filename LIKE '%' || :query || '%' ORDER BY lastModified DESC")
    suspend fun searchFiles(query: String): List<FileEntity>
}
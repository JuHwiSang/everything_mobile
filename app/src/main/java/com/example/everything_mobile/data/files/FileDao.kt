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

    /**
     * @param sortMode 정렬 모드 (0:이름, 1:크기, 2:날짜)
     */
    @Query("""
        SELECT * FROM files 
        ORDER BY 
            -- 1순위: 폴더 우선 정렬 (1=Directory, 0=File 이므로 내림차순)
            fileType DESC, 
            
            -- 2순위: 선택한 모드에 따른 정렬
            CASE WHEN :sortMode = 0 THEN filename END ASC,       -- 0: 이름 (가나다순)
            CASE WHEN :sortMode = 1 THEN size END DESC,          -- 1: 크기 (큰것부터)
            CASE WHEN :sortMode = 2 THEN lastModified END DESC,  -- 2: 날짜 (최신부터)
            
            -- 3순위: 값이 같거나 나머지 경우 이름순 보정
            filename ASC
    """)
    suspend fun getAllFiles(sortMode: Int = 0): List<FileEntity>

    /**
     * @param sortMode 정렬 모드 (0:이름, 1:크기, 2:날짜)
     */
    @Query("""
        SELECT * FROM files 
        WHERE (:query IS NULL OR :query = '' OR filename LIKE '%' || :query || '%')
        ORDER BY 
            -- 1순위: 폴더 우선 정렬 (1=Directory, 0=File 이므로 내림차순)
            fileType DESC, 
            
            -- 2순위: 선택한 모드에 따른 정렬
            CASE WHEN :sortMode = 0 THEN filename END ASC,       -- 0: 이름 (가나다순)
            CASE WHEN :sortMode = 1 THEN size END DESC,          -- 1: 크기 (큰것부터)
            CASE WHEN :sortMode = 2 THEN lastModified END DESC,  -- 2: 날짜 (최신부터)
            
            -- 3순위: 값이 같거나 나머지 경우 이름순 보정
            filename ASC
    """)
    suspend fun searchFiles(query: String, sortMode: Int = 0): List<FileEntity>
}
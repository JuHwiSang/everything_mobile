package com.example.everything_mobile.data.files

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import androidx.room.Update
import com.example.everything_mobile.data.files.FileEntity
import com.example.everything_mobile.data.files.ScanResult

@Dao
abstract class FileDao { // interface -> abstract class 변경

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(files: List<FileEntity>) // abstract 추가

    @Update
    abstract suspend fun updateAll(files: List<FileEntity>) // abstract 추가

    @Query("DELETE FROM files WHERE path IN (:paths)")
    abstract suspend fun deleteByPaths(paths: List<String>) // abstract 추가

    @Query("SELECT * FROM files")
    abstract suspend fun getAllFilesSync(): List<FileEntity> // abstract 추가

    // 로직이 있는 함수는 open/final 상관없음 (그대로 둠)
    @Transaction
    open suspend fun applyDiff(result: ScanResult) {
        if (result.toInsert.isNotEmpty()) insertAll(result.toInsert)
        if (result.toUpdate.isNotEmpty()) updateAll(result.toUpdate)
        if (result.toDelete.isNotEmpty()) deleteByPaths(result.toDelete)
    }

    @Query("""
        SELECT * FROM files 
        ORDER BY 
            CASE WHEN :sortMode = 0 THEN fileType END DESC, 
            CASE WHEN :sortMode = 0 THEN filename END ASC,
            CASE WHEN :sortMode = 1 THEN size END DESC,
            CASE WHEN :sortMode = 2 THEN lastModified END DESC,
            filename ASC
    """)
    abstract suspend fun getAllFiles(sortMode: Int = 0): List<FileEntity> // abstract 추가

    // [수정됨] 1. 외부에서 호출하는 안전한 래퍼 함수
    suspend fun searchFilesFts(query: String, sortMode: Int = 0): List<FileEntity> {
        // 입력값을 따옴표로 감싸서 FTS 문법 에러(크래시) 방지
        // 내부에 "가 있으면 ""로 이스케이프 처리
        val safeQuery = "\"${query.replace("\"", "\"\"")}\""

        // 바인딩 파라미터로 넘기므로 SQL Injection 안전함
        return _searchFilesFtsInternal(safeQuery, sortMode)
    }

    // [수정됨] 2. 실제 쿼리 (protected abstract로 변경, 이름 변경)
    @SkipQueryVerification
    @Query("""
        SELECT f.* FROM files AS f
        JOIN files_fts AS fts ON f.path = fts.path
        WHERE fts.filename MATCH :safeQuery
        ORDER BY 
            CASE WHEN :sortMode = 0 THEN f.fileType END DESC,
            CASE WHEN :sortMode = 0 THEN f.filename END ASC,
            CASE WHEN :sortMode = 1 THEN f.size END DESC,
            CASE WHEN :sortMode = 2 THEN f.lastModified END DESC,
            f.filename ASC
    """)
    protected abstract suspend fun _searchFilesFtsInternal(safeQuery: String, sortMode: Int): List<FileEntity>

    @Query("""
        SELECT f.* FROM files AS f
        WHERE f.filename LIKE '%' || :query || '%'
        ORDER BY 
            (CASE WHEN :sortMode = 0 THEN f.fileType END) DESC,
            (CASE WHEN :sortMode = 1 THEN f.size END) DESC,
            (CASE WHEN :sortMode = 0 THEN f.filename END) ASC,
            (CASE WHEN :sortMode = 2 THEN f.lastModified END) DESC,
            f.filename ASC
    """)
    abstract suspend fun searchFiles(query: String, sortMode: Int = 0): List<FileEntity> // abstract 추가
}
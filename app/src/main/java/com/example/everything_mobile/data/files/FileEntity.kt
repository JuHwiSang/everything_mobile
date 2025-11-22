package com.example.everything_mobile.data.files

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class FileEntity(
    // 파일 경로가 고유 ID(PK) 역할
    @PrimaryKey
    val path: String,
    val lastModified: Long,
    val size: Long,
    val fileType: Int
) {
    companion object {
        const val TYPE_FILE = 0
        const val TYPE_DIRECTORY = 1
        const val TYPE_SYMLINK = 2
    }
}
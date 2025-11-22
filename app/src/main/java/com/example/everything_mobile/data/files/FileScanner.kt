package com.example.everything_mobile.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 1. 결과(Diff)를 담을 그릇
data class ScanResult(
    val toInsert: List<FileEntity>, // 추가될 녀석들
    val toUpdate: List<FileEntity>, // 수정될 녀석들
    val toDelete: List<String>      // 삭제될 경로들
)

object FileScanner {
    init {
        System.loadLibrary("native-scanner")
    }

    // 2. JNI 함수 선언 변경
    // 인자: (기존 경로들, 기존 시간들) -> 배열 2개로 넘기는 게 JNI에서 제일 빠름
    private external fun nativeScanDiff(
        oldPaths: Array<String>,
        oldTimes: LongArray
    ): ScanResult

    // 3. 사용하는 함수
    suspend fun syncFiles(currentDbData: List<FileEntity>): ScanResult = withContext(Dispatchers.Default) {
        // DB 데이터를 배열 2개로 쪼개서 C++로 보냄 (마샬링 비용 최소화)
        val paths = currentDbData.map { it.path }.toTypedArray()
        val times = currentDbData.map { it.lastModified }.toLongArray()

        nativeScanDiff(paths, times)
    }
}
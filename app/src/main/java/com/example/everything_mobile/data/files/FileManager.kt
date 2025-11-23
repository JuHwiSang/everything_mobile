package com.example.everything_mobile.data.files

// DAO와 Scanner를 둘 다 알고 있는 유일한 녀석
class FileManager constructor(
    private val fileDao: FileDao
) {
    // ViewModel은 이 함수 하나만 호출하면 됨.
    suspend fun scanAndSync() {
        // 1. DB에서 현재 상태 가져오기 (스냅샷)
        // (Repository가 알아서 함)
        val currentFiles = fileDao.getAllFilesSync()

        // 2. C++ 엔진에게 Diff 계산 맡기기
        // (Repository가 알아서 함)
        val diffResult = FileScanner.syncFiles(currentFiles)

        // 3. 결과 DB에 반영하기 (트랜잭션)
        // (Repository가 알아서 함)
        // *참고: runInTransaction은 RoomDatabase 인스턴스가 필요하므로
        // 실제로는 DAO에 @Transaction 메서드를 만들거나 Database 객체를 주입받아야 함.
        // 여기선 편의상 DAO가 처리한다고 가정.
        fileDao.applyDiff(diffResult)
    }

    suspend fun searchFiles(query: String? = ""): List<FileEntity> {
        return if (query.isNullOrBlank()) {
            fileDao.getAllFiles()
        } else if (query.length < 3) { // FTS5는 3글지 이하의, 1글자나 2글자 검색을 못함
            fileDao.searchFiles(query)
        } else {
            fileDao.searchFilesFts(query)
        }
    }
}
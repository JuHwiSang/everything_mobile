package com.example.everything_mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.everything_mobile.data.files.FileDao
import com.example.everything_mobile.data.files.FileEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [FileEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // 2. DAO를 꺼내는 추상 함수 (Room이 알아서 구현해줌)
    abstract fun fileDao(): FileDao

    // 3. 싱글톤 패턴 (어디서든 AppDatabase.getInstance(context)로 접근 가능하게)
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // 이미 만들어져 있으면 그거 리턴
            return INSTANCE ?: synchronized(this) {
                // 없으면 새로 만듦
                System.loadLibrary("sqlcipher")

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "everything-db" // DB 파일 이름
                )
                    .openHelperFactory(SupportOpenHelperFactory(ByteArray(0)))
//                    .fallbackToDestructiveMigration(true) // 나중에 스키마 바꾸면 DB 초기화 (개발때 편함)
                    .addCallback(ftsTriggerCallback)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        val ftsTriggerCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // DB 처음 생성될 때 트리거 설치
                createTriggers(db)
            }

            // 필요하다면 onOpen에서도 트리거 존재 여부 체크 가능
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                createTriggers(db)
            }

            private fun createTriggers(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS files_fts 
                    USING fts5(filename, path UNINDEXED, tokenize='trigram');
                """)

                // 1. INSERT 트리거
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS t_files_insert AFTER INSERT ON files
                    BEGIN
                        INSERT INTO files_fts(filename, path) VALUES(new.filename, new.path);
                    END;
                """
                )

                // 2. DELETE 트리거
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS t_files_delete AFTER DELETE ON files
                    BEGIN
                        DELETE FROM files_fts WHERE path = old.path;
                    END;
                """
                )

                // 3. UPDATE 트리거
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS t_files_update AFTER UPDATE ON files
                    BEGIN
                        UPDATE files_fts SET filename = new.filename WHERE path = old.path;
                    END;
                """
                )
            }
        }
    }
}
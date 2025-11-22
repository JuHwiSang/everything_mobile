package com.example.everything_mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.everything_mobile.data.files.FileDao
import com.example.everything_mobile.data.files.FileEntity

@Database(entities = [FileEntity::class], version = 1, exportSchema = false)
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
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "everything-db" // DB 파일 이름
                )
                    //.fallbackToDestructiveMigration() // 나중에 스키마 바꾸면 DB 초기화 (개발때 편함)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
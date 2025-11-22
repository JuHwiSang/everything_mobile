package com.example.everything_mobile

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.everything_mobile.data.AppDatabase
import com.example.everything_mobile.data.files.FileManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val appDatabase = AppDatabase.getInstance(this);
        val fileManager = FileManager(appDatabase.fileDao())
        lifecycleScope.launch {
            fileManager.scanAndSync()

//            val query = "CI"
//            val results = fileManager.searchFiles(query) // 또는 dao.searchFiles(query)
//
//            // 3. 로그 찍기 (스마트하게)
//            Log.d("TEST", "=== 검색 결과: '$query' ===")
//            Log.d("TEST", "총 개수: ${results.size}개") // 일단 개수부터 확인
//
//            // [주의] 리스트 전체를 toString()하면 로그 잘림.
//            // 앞부분 10개만 눈으로 확인하는 게 국룰.
//            results.take(10).forEach { file ->
//                Log.d("TEST", "발견: $file")
//                // data class라서 "FileEntity(path=..., size=...)" 이렇게 예쁘게 나옴
//            }
//
//            if (results.size > 10) {
//                Log.d("TEST", "... 외 ${results.size - 10}개 더 있음")
//            }
        }
    }
}
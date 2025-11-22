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

    private val PERMISSION_REQUEST_CODE = 1001
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 1. RecyclerView 찾아오기
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvFileList)

        // 2. 가짜 데이터 만들기 (테스트용)
        val testData = listOf(
            FileData("내 문서", "폴더 • 2025-11-21", true),
            FileData("과제.docx", "1.2MB • 2025-11-20", false),
            FileData("여행사진.jpg", "4.5MB • 2025-10-24", false)
        )

        // 3. 어댑터 연결하기
        val adapter = FileAdapter(testData)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this) // 리스트 모양 결정

        // 권한 체크 실행
        checkAndRequestPermissions()

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

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 안드로이드 11 (API 30) 이상: MANAGE_EXTERNAL_STORAGE 체크
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, PERMISSION_REQUEST_CODE)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, PERMISSION_REQUEST_CODE)
                }
            } else {
                // 권한이 이미 있음 -> C++ 스캔 로직 시작 (여기에 연결)
                startFileScan()
            }
        } else {
            // 안드로이드 10 이하: 기존 권한 체크
            val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (readPermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                // 권한이 이미 있음
                startFileScan()
            }
        }
    }

    // 사용자가 권한 설정 화면에서 돌아왔을 때 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "권한이 허용되었습니다. 스캔을 시작합니다.", Toast.LENGTH_SHORT).show()
                    startFileScan()
                } else {
                    Toast.makeText(this, "파일 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 안드로이드 10 이하 권한 요청 결과 처리
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startFileScan()
            } else {
                Toast.makeText(this, "권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startFileScan() {
        // TODO: 여기서 JNI를 통해 C++ 네이티브 스캔 함수를 호출
        // nativeScanner.scanAndSyncDB()
    }
}
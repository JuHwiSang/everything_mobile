package com.example.everything_mobile

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.everything_mobile.data.AppDatabase
import com.example.everything_mobile.data.files.FileManager
import kotlinx.coroutines.launch
import android.widget.ImageButton // 추가
import androidx.appcompat.app.AlertDialog // 추가

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.widget.doOnTextChanged
import com.example.everything_mobile.data.files.FileEntity
import java.io.File
import java.net.URLEncoder

fun FileEntity.toFileData(): FileData {
    return FileData(
        name = this.filename,
        size = this.size,
        date = this.lastModified,
        path = this.path,
        isFolder = this.fileType == FileEntity.TYPE_DIRECTORY
    )
}

class MainActivity : AppCompatActivity() {
    private lateinit var fileManager: FileManager
    private lateinit var appDatabase: AppDatabase
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: FileAdapter
    private lateinit var etSearch: EditText

    private val PERMISSION_REQUEST_CODE = 1001

    private var fileList = listOf<FileData>()
    private var currentSortMode = 0 // 0:이름, 1:크기, 2:날짜
    
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
        recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvFileList)

        // 2. 가짜 데이터 만들기 (테스트용)
//        val testData = listOf(
//            FileData("내 문서", "폴더 • 2025-11-21", true),
//            FileData("과제.docx", "1.2MB • 2025-11-20", false),
//            FileData("여행사진.jpg", "4.5MB • 2025-10-24", false)
//        )

        // 3. 어댑터 연결하기
        adapter = FileAdapter(listOf<FileData>())
        adapter.onItemClick = { clickedItem ->
            if (clickedItem.isFolder) {
                openDirectory(clickedItem.path)
            } else {
                openFile(clickedItem.path)
//                val file = File(clickedItem.details)
//
//                // 2. 그 파일의 '부모 폴더'를 찾습니다.
//                // 예: "/storage/emulated/0/Download"
//                val parentDir = file.parentFile
//
//                if (parentDir != null && parentDir.exists()) {
//                    Log.d("onCreate open", "open parent directory: ${parentDir.path}")
//                    openDirectory(parentDir.path)
//                }
            }
        }

        adapter.onItemLongClick = { clickedItem, menu ->
            if (!clickedItem.isFolder) {
                menu.add(0, 0, 0, "파일 열기").setOnMenuItemClickListener {
                    openFile(clickedItem.path)
                    true
                }

                menu.add(0, 1, 1, "파일 위치 열기").setOnMenuItemClickListener {
                    openParentDirectory(clickedItem.path)
                    true
                }
            }
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this) // 리스트 모양 결정

        appDatabase = AppDatabase.getInstance(this);
        fileManager = FileManager(appDatabase.fileDao())

        etSearch = findViewById<EditText>(R.id.etSearch)
        etSearch.doOnTextChanged { text, start, before, count ->
            lifecycleScope.launch {
                refreshListView()
            }
        } 

        // ★ 추가된 부분: 정렬 버튼 연결
        val btnSort = findViewById<ImageButton>(R.id.btnSort)
        btnSort.setOnClickListener {
            showSortDialog()
        }

        // 권한 체크 실행
        checkAndRequestPermissions()
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
        lifecycleScope.launch {
            fileManager.scanAndSync()
            refreshListView()
        }
    }

    private suspend fun refreshListView() {
        adapter.updateList(
            fileManager.searchFiles(etSearch.text.toString(), currentSortMode)
                .map { entity -> entity.toFileData() }
        )
    }

    private fun openDirectory(path: String) {
        // 1. "내장 메모리 기본 경로" (/storage/emulated/0/) 를 잘라냅니다.
        // 결과 예시: DCIM/Camera
        val rootPath = Environment.getExternalStorageDirectory().path
        var relativePath = path.removePrefix(rootPath)

        // 경로 앞에 붙은 '/' 제거 (혹시 있다면)
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1)
        }

        // 2. 안드로이드 시스템이 알아먹는 포맷으로 변경
        // 포맷: content://com.android.externalstorage.documents/document/primary:폴더명
        // 주의: URL 인코딩이 필요함 (띄어쓰기나 특수문자 처리)
        val encodedPath = URLEncoder.encode(relativePath, "UTF-8").replace("+", "%20")
        val uriString = "content://com.android.externalstorage.documents/document/primary:$encodedPath"
        val uri = uriString.toUri()

        // 3. 인텐트 실행
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "vnd.android.document/directory") // 표준 폴더 MIME 타입
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // 만약 표준 방식이 안 먹히면 Samsung 전용 방식 등으로 시도 (Fallback)
            // 하지만 위 방식이 대부분의 'Files by Google'이나 'Samsung MyFiles'에서 작동함
            e.printStackTrace()
        }
    }

    private fun openFile(path: String) {
        val file = File(path)

        if (!file.exists()) {
            Toast.makeText(this, "파일이 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. MIME Type 알아내기
        val extension = file.extension.lowercase() // 확장자 추출 (소문자로)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "*/*" // 못 찾으면 모든 타입(*/*)

        // 2. Intent 생성 (ACTION_VIEW)
        val intent = Intent(Intent.ACTION_VIEW)

        // 3. URI 생성 (FileProvider 사용 필수!)
        // 주의: "com.example.yourapp.provider" 부분은 AndroidManifest의 authorities와 일치해야 합니다.
        val authority = "${packageName}.provider"
        val uri = FileProvider.getUriForFile(this, authority, file)

        // 4. 데이터와 타입 설정 및 권한 부여
        intent.setDataAndType(uri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 외부 앱이 이 파일을 읽을 수 있도록 허용
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 필요시 태스크 분리

        // 5. 앱 실행 시도
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 해당 파일을 열 수 있는 앱이 설치되어 있지 않은 경우
            Toast.makeText(this, "이 파일을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openParentDirectory(path: String) {
        val parent = File(path).parent
        if (!parent.isNullOrBlank()) {
            openDirectory(parent)
        }
    }

    // 1. 정렬 팝업창 띄우기
    private fun showSortDialog() {
        val options = arrayOf("이름 순", "크기 순 (큰 것부터)", "날짜 순 (최신)")
        
        AlertDialog.Builder(this)
            .setTitle("정렬 기준 선택")
            .setSingleChoiceItems(options, currentSortMode) { dialog, which ->
                currentSortMode = which
                dialog.dismiss()

                lifecycleScope.launch {
                    refreshListView()
                }
            }
            .show()
    }
}

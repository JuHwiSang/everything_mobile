#include <jni.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <dirent.h>
#include <sys/stat.h>
#include <android/log.h>
#include <chrono>
#include <stack>

#define LOG_TAG "NativeScanner"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 파일 타입 상수 (Kotlin FileEntity와 일치)
const int TYPE_FILE = 0;
const int TYPE_DIRECTORY = 1;
const int TYPE_SYMLINK = 2;

struct FileInfo {
    std::string filename;
    std::string path;
    long long mtime;
    long long size;
    int fileType;
};

std::vector<FileInfo> inserts;
std::vector<FileInfo> updates;
std::vector<std::string> deletes;
std::unordered_map<std::string, long long> dbSnapshot;

// [핵심 로직] 검색 제외 대상인지 판별
// true를 리턴하면 DB에도 안 넣고, 폴더 안으로 들어가지도 않음.
bool isExcluded(const std::string& fullPath) {
    // 1. 가상 파일 시스템 (안전장치)
    // 여기 들어가면 폰 멈추거나 무한 루프 돔
    if (fullPath.find("/proc") == 0 || fullPath.find("/sys") == 0 || fullPath.find("/dev") == 0) return true;

    // 2. [요청사항] 명시적 제외 경로들
    // 2-1. Android 11+ 보안 폴더 (접근 시 퍼포먼스 저하 및 권한 에러)
    if (fullPath == "/storage/emulated/0/Android/data") return true;
    if (fullPath == "/storage/emulated/0/Android/obb") return true; // 보통 data랑 같이 제외함

    // 2-2. 시스템 복구/쓰레기통 폴더
    if (fullPath == "/storage/emulated/0/lost+found") return true;
    if (fullPath == "/storage/emulated/0/.Trash") return true; // 삼성 등 제조사 쓰레기통

    // (.thumbnails도 제외하고 싶으면 주석 해제)
    // if (fullPath.find("/.thumbnails") != std::string::npos) return true;

    return false;
}

// 반복문(Iterative) DFS 탐색
void scanIterative(const std::string& rootPath) {
    std::vector<std::string> stack;
    stack.reserve(1024);
    stack.push_back(rootPath);

    while (!stack.empty()) {
        std::string currentPath = stack.back();
        stack.pop_back();

        // [1차 필터] 폴더 열기 전에 경로 자체를 검사 (성능 최적화)
        if (isExcluded(currentPath)) continue;

        DIR* dir = opendir(currentPath.c_str());
        if (!dir) {
            // 권한 없어서 못 여는 폴더는 조용히 스킵 (Everything 철학)
            continue;
        }

        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;

            std::string fullPath = currentPath + "/" + entry->d_name;

            // [2차 필터] 하위 파일/폴더에 대해서도 제외 검사
            // (예: Android 폴더 안의 data 폴더를 만났을 때 여기서 걸러짐)
            if (isExcluded(fullPath)) continue;

            // 정보 획득 (lstat: 링크 자체 정보)
            struct stat st;
            if (lstat(fullPath.c_str(), &st) == -1) continue;

            // 타입 결정
            int type = TYPE_FILE;
            if (S_ISLNK(st.st_mode)) type = TYPE_SYMLINK;
            else if (S_ISDIR(st.st_mode)) type = TYPE_DIRECTORY;

            // DB 비교 및 리스트 추가 로직
            auto it = dbSnapshot.find(fullPath);
            if (it == dbSnapshot.end()) {
                inserts.push_back({entry->d_name, fullPath, (long long)st.st_mtim.tv_sec, (long long)st.st_size, type});
            } else {
                if (it->second != st.st_mtim.tv_sec) {
                    updates.push_back({entry->d_name, fullPath, (long long)st.st_mtim.tv_sec, (long long)st.st_size, type});
                }
                dbSnapshot.erase(it);
            }

            // 재귀 조건: 진짜 디렉토리인 경우에만 스택에 추가
            // (심볼릭 링크는 따라가지 않음 -> 무한 루프 방지)
            if (type == TYPE_DIRECTORY) {
                stack.push_back(fullPath);
            }
        }
        closedir(dir);
    }
}

extern "C" JNIEXPORT jobject JNICALL
// 패키지명 주의: everything_mobile -> everything_1mobile
Java_com_example_everything_1mobile_data_files_FileScanner_nativeScanDiff(
        JNIEnv* env,
        jobject thiz,
        jobjectArray oldPaths,
        jlongArray oldTimes) {

    auto start = std::chrono::high_resolution_clock::now();

    // 초기화
    inserts.clear();
    updates.clear();
    deletes.clear();
    dbSnapshot.clear();

    // Kotlin -> C++ 스냅샷 로드
    int count = env->GetArrayLength(oldPaths);
    jlong* timePtr = env->GetLongArrayElements(oldTimes, nullptr);

    for (int i = 0; i < count; i++) {
        jstring pathStr = (jstring)env->GetObjectArrayElement(oldPaths, i);
        const char* pathChars = env->GetStringUTFChars(pathStr, nullptr);
        dbSnapshot[std::string(pathChars)] = timePtr[i];
        env->ReleaseStringUTFChars(pathStr, pathChars);
        env->DeleteLocalRef(pathStr);
    }
    env->ReleaseLongArrayElements(oldTimes, timePtr, 0);

    // [검색 시작점 지정]
    // 1. 내장 메모리 (기본)
    scanIterative("/storage/emulated/0");

    // 2. [SD 카드 - 선택 구현]
    // 나중에 SD 카드를 지원하려면 여기에 경로를 추가하면 됨
    // 예: scanIterative("/storage/XXXX-XXXX");

    // DB에 남은 경로 = 삭제된 파일 처리
    for (const auto& pair : dbSnapshot) {
        deletes.push_back(pair.first);
    }

    jclass fileEntityClass = env->FindClass("com/example/everything_mobile/data/files/FileEntity");

    // [수정 1] 생성자 시그니처 변경
    // 기존: (Ljava/lang/String;JJI)V  -> (String(path), long, long, int)
    // 변경: (Ljava/lang/String;Ljava/lang/String;JJI)V -> (String(filename), String(path), long, long, int)
    //                               ^^^^^^^^^^^^^^^^^^ String이 하나 더 추가됨
    jmethodID fileConstructor = env->GetMethodID(fileEntityClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;JJI)V");

    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    jobject insertList = env->NewObject(arrayListClass, arrayListInit);
    jobject updateList = env->NewObject(arrayListClass, arrayListInit);
    jobject deleteList = env->NewObject(arrayListClass, arrayListInit);

    // [수정 2] Insert 루프 수정 (filename 추가)
    for (const auto& item : inserts) {
        jstring name = env->NewStringUTF(item.filename.c_str()); // filename 문자열 생성
        jstring path = env->NewStringUTF(item.path.c_str());

        // 생성자에 name을 첫 번째 인자로 전달
        jobject entity = env->NewObject(fileEntityClass, fileConstructor, name, path, item.mtime, item.size, (jint)item.fileType);

        env->CallBooleanMethod(insertList, arrayListAdd, entity);

        // 메모리 해제 (name도 해제해야 함!)
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(path);
        env->DeleteLocalRef(entity);
    }

    // [수정 3] Update 루프 수정 (filename 추가)
    for (const auto& item : updates) {
        jstring name = env->NewStringUTF(item.filename.c_str()); // filename 문자열 생성
        jstring path = env->NewStringUTF(item.path.c_str());

        // 생성자에 name을 첫 번째 인자로 전달
        jobject entity = env->NewObject(fileEntityClass, fileConstructor, name, path, item.mtime, item.size, (jint)item.fileType);

        env->CallBooleanMethod(updateList, arrayListAdd, entity);

        // 메모리 해제
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(path);
        env->DeleteLocalRef(entity);
    }

    // Delete
    for (const auto& pathStr : deletes) {
        jstring path = env->NewStringUTF(pathStr.c_str());
        env->CallBooleanMethod(deleteList, arrayListAdd, path);
        env->DeleteLocalRef(path);
    }

    jclass scanResultClass = env->FindClass("com/example/everything_mobile/data/files/ScanResult");
    jmethodID scanResultInit = env->GetMethodID(scanResultClass, "<init>", "(Ljava/util/List;Ljava/util/List;Ljava/util/List;)V");

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start).count();

    LOGD("==== [Native Performance] 총 소요시간: %lld ms (추가:%zu, 수정:%zu, 삭제:%zu) ====",
         duration, inserts.size(), updates.size(), deletes.size());

    return env->NewObject(scanResultClass, scanResultInit, insertList, updateList, deleteList);
}
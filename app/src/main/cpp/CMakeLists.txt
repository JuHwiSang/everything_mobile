# 최소 CMake 버전 설정
cmake_minimum_required(VERSION 3.22.1)

# 프로젝트 이름
project("everything_mobile")

# 라이브러리 정의 (이름: native-scanner)
add_library(
        native-scanner
        SHARED
        native-scanner.cpp) # 소스 파일 지정

# 안드로이드 로그 라이브러리 찾기
find_library(
        log-lib
        log)

# 라이브러리 링크 (내 코드 + 로그)
target_link_libraries(
        native-scanner
        ${log-lib})
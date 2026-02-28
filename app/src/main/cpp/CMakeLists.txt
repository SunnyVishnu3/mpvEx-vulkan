cmake_minimum_required(VERSION 3.22)
project(mpvex-adrenotools)

add_library(adrenotools_bridge SHARED adrenotools_bridge.cpp)

# Link against the pre-built libadrenotools.so you placed in jniLibs
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -L${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

target_link_libraries(adrenotools_bridge
    adrenotools
    log
)

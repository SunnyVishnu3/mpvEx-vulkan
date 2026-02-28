cmake_minimum_required(VERSION 3.22)
project(mpvex-adrenotools)

# 1. Compile libadrenotools directly from the downloaded source folder
add_subdirectory(libadrenotools)

# 2. Compile our custom Kotlin-to-C++ bridge
add_library(adrenotools_bridge SHARED adrenotools_bridge.cpp)

# 3. Tell our bridge where to find the official adrenotools headers
target_include_directories(adrenotools_bridge PRIVATE libadrenotools/include)

# 4. Link our bridge to the freshly compiled adrenotools library
target_link_libraries(adrenotools_bridge
    adrenotools
    log
)

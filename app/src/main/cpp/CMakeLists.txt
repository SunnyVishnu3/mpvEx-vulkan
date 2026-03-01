cmake_minimum_required(VERSION 3.22)
project(mpvex-adrenotools)

# 1. Compile libadrenotools
add_subdirectory(libadrenotools)

# 2. Compile our custom Kotlin-to-C++ bridge
add_library(adrenotools_bridge SHARED adrenotools_bridge.cpp)
target_include_directories(adrenotools_bridge PRIVATE libadrenotools/include)
target_link_libraries(adrenotools_bridge adrenotools log)

# 3. THE MAGIC "BAIT" LIBRARY
file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/dummy_bait.cpp "void dummy_bait() {}")
add_library(vulkan_freedreno SHARED ${CMAKE_CURRENT_BINARY_DIR}/dummy_bait.cpp)

# 4. FORCE CMake to build the bait file!
add_dependencies(adrenotools_bridge vulkan_freedreno)

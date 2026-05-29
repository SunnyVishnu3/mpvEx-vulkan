cmake_minimum_required(VERSION 4.1.2)

# Keep FetchContent subprojects (libadrenotools, etc.) buildable under CMake
# 4.x even though some declare `cmake_minimum_required(VERSION 3.x)`. CMake 4
# drops compatibility below 3.5; this baseline replays older defaults for
# fetched projects so they don't fail policy-OLD warnings.
set(CMAKE_POLICY_VERSION_MINIMUM 3.5 CACHE STRING "" FORCE)

project("streamx_native" LANGUAGES C CXX)

# =============================================================================
# Project-wide build settings
# =============================================================================

# Pin standards explicitly so FetchContent submodules don't downgrade us.
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Hidden visibility by default — every JNI entry point is JNIEXPORT and stays
# exported. Saves PLT slots, shrinks the dynamic symbol table, and lets the
# linker inline cross-TU calls more aggressively under LTO.
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

# Position-independent code is required on Android. AGP already passes -fPIC,
# but pinning it at the CMake level guarantees consistency across submodules.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Drop transitive PRIVATE/static-lib dependencies from link lines when the
# final target doesn't actually need them (CMake 3.19+). Trims .so size,
# shortens link commands, and prunes dead DT_NEEDED entries.
set(CMAKE_OPTIMIZE_DEPENDENCIES ON)

# Honor target-level INTERPROCEDURAL_OPTIMIZATION even if a parent scope set
# CMAKE_INTERPROCEDURAL_OPTIMIZATION conditionally. CMP0069 NEW = required.
cmake_policy(SET CMP0069 NEW)
set(CMAKE_POLICY_DEFAULT_CMP0069 NEW)

# Default CMAKE_BUILD_TYPE for single-config gens that forget to pass one.
# AGP always sets one explicitly, but this protects standalone CMake runs.
if (NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

# Convenience flag — true for any build that should be optimized for runtime.
set(IS_OPTIMIZED_BUILD FALSE)
if (CMAKE_BUILD_TYPE STREQUAL "Release" OR CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo" OR CMAKE_BUILD_TYPE STREQUAL "MinSizeRel")
    set(IS_OPTIMIZED_BUILD TRUE)
endif()

# ThinLTO globally for optimized builds. INTERPROCEDURAL_OPTIMIZATION on clang
# translates to -flto=thin (vs. monolithic -flto), which keeps incremental
# link times reasonable while still doing cross-TU inlining and DCE.
if (IS_OPTIMIZED_BUILD)
    include(CheckIPOSupported)
    check_ipo_supported(RESULT _ipo_ok OUTPUT _ipo_err)
    if (_ipo_ok)
        set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
        message(STATUS "IPO/ThinLTO enabled for ${CMAKE_BUILD_TYPE}")
    else()
        message(WARNING "IPO not supported by this toolchain: ${_ipo_err}")
    endif()
endif()

# Aggressive link-time stripping & merging applied to every target we build
# in this project. Submodule targets (adrenotools, bytehook hooks) inherit
# these via add_link_options so the final .so stays tight regardless of how
# bloated the upstream code is.
#   --gc-sections        : drop unreferenced sections (paired with -ffunction/-fdata-sections)
#   --icf=safe           : identical code folding (lld) — merges byte-identical functions
#   --as-needed          : drop DT_NEEDED entries the binary doesn't actually use
#   -z relro,-z now      : full RELRO (security; also lets the linker skip lazy resolution)
#   -z noexecstack       : NX stack
#   --hash-style=gnu     : smaller, faster .gnu.hash than legacy SysV
#   --exclude-libs,ALL   : strip static-archive symbols from the dynamic exports
#                          (we re-add it per-target below; it must come AFTER all -l args)
if (IS_OPTIMIZED_BUILD)
    add_link_options(
        "LINKER:--gc-sections"
        "LINKER:--icf=safe"
        "LINKER:--as-needed"
        "LINKER:-z,relro,-z,now,-z,noexecstack"
        "LINKER:--hash-style=gnu"
    )
endif()

# Compile flags shared by every target we directly build. Submodules manage
# their own flags (we don't want to override their feature checks).
add_compile_options(
    -Wall
    -ffunction-sections
    -fdata-sections
)
if (IS_OPTIMIZED_BUILD)
    add_compile_options(-O3 -ffast-math)
endif()

# =============================================================================
# Adrenotools bridge — custom Vulkan driver loader for Qualcomm Adreno GPUs.
# arm64-v8a only (libadrenotools requirement).
# =============================================================================
if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    include(FetchContent)

    # Upstream bylaws/libadrenotools is the canonical fork. The previous
    # eden-emulator fork added adrenotools_set_freedreno_env() — we replicate
    # that behavior via plain setenv() in ApplyEnvironmentVariable, so we no
    # longer depend on the eden-specific extensions.
    #
    # EXCLUDE_FROM_ALL: only build adrenotools targets we actually link
    # against; skip any of their tests/tools that might exist now or later.
    # SYSTEM: mark adrenotools' include paths as -isystem so their headers'
    # warnings are silenced regardless of -Wall in this project.
    FetchContent_Declare(
        adrenotools
        GIT_REPOSITORY    https://github.com/bylaws/libadrenotools.git
        GIT_TAG           master
        GIT_SHALLOW       TRUE
        EXCLUDE_FROM_ALL  TRUE
        SYSTEM
    )

    FetchContent_MakeAvailable(adrenotools)

    # Belt-and-braces: SYSTEM on FetchContent_Declare covers most generators,
    # but older Ninja paths still respect -Wall on the included headers. Hard
    # -w on the internal targets gives a stable warning floor.
    foreach(_at_target adrenotools hook_impl main_hook file_redirect_hook gsl_alloc_hook)
        if (TARGET ${_at_target})
            target_compile_options(${_at_target} PRIVATE -w)
            # Match our visibility/IPO posture so the linker can fold/strip
            # their code into our final .so on equal footing.
            set_target_properties(${_at_target} PROPERTIES
                C_VISIBILITY_PRESET hidden
                CXX_VISIBILITY_PRESET hidden
                VISIBILITY_INLINES_HIDDEN ON
                POSITION_INDEPENDENT_CODE ON
            )
            if (IS_OPTIMIZED_BUILD AND _ipo_ok)
                set_target_properties(${_at_target} PROPERTIES INTERPROCEDURAL_OPTIMIZATION TRUE)
            endif()
        endif()
    endforeach()

    # ByteHook — PLT-hook engine. The Android Gradle Plugin's `prefab = true`
    # exposes the AAR's prefab/ directory; find_package picks up the
    # bytehookConfig.cmake shipped inside com.bytedance:bytehook.
    find_package(bytehook REQUIRED CONFIG)

    add_library(adrenotools_bridge SHARED adrenotools_bridge.cpp)

    # SYSTEM include — adrenotools headers won't trip our -Wall.
    target_include_directories(adrenotools_bridge SYSTEM PRIVATE
        ${adrenotools_SOURCE_DIR}/include
    )

    target_link_libraries(adrenotools_bridge PRIVATE
        android
        log
        EGL
        adrenotools
        bytehook::bytehook
    )

    target_link_options(adrenotools_bridge PRIVATE "LINKER:--exclude-libs,ALL")
endif()

if (NOT CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    message(STATUS "Skipping adrenotools_bridge for non-arm64 architecture: ${CMAKE_ANDROID_ARCH_ABI}")
endif()

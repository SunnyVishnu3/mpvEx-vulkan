#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <unistd.h>
#include <android/log.h>
#include <stdlib.h>
#include "adrenotools/driver.h" 

#define LOG_TAG "AdrenoToolsBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// The hooked libvulkan handle MUST stay alive for the lifetime of the process,
// otherwise the linker may drop the Turnip mapping and later dlopen("libvulkan.so")
// calls will fall back to the system Adreno driver.
static void* g_hooked_libvulkan = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_app_marlboroadvance_mpvex_system_AdrenoTools_nativeSetEnv(
        JNIEnv *env, jobject thiz, jstring name, jstring value) {
    
    const char *c_name = env->GetStringUTFChars(name, nullptr);
    const char *c_value = env->GetStringUTFChars(value, nullptr);

    int result = setenv(c_name, c_value, 1);

    env->ReleaseStringUTFChars(name, c_name);
    env->ReleaseStringUTFChars(value, c_value);

    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_marlboroadvance_mpvex_system_AdrenoTools_nativeHookDriver(
        JNIEnv *env, jobject thiz, jstring tmpLibDir, jstring hookLibDir, jstring customDriverDir, jstring driverName) {
    
    const char *tmp_dir = env->GetStringUTFChars(tmpLibDir, nullptr);
    const char *hook_dir = env->GetStringUTFChars(hookLibDir, nullptr);
    const char *driver_dir = env->GetStringUTFChars(customDriverDir, nullptr);
    const char *d_name = env->GetStringUTFChars(driverName, nullptr);

    // adrenotools concatenates path + filename internally; without a trailing
    // slash the resulting path is invalid and dlopen silently fails.
    auto with_slash = [](const char* s) {
        std::string r(s ? s : "");
        if (!r.empty() && r.back() != '/') r.push_back('/');
        return r;
    };
    std::string tmp_dir_s    = with_slash(tmp_dir);
    std::string hook_dir_s   = with_slash(hook_dir);
    std::string driver_dir_s = with_slash(driver_dir);

    LOGI("Starting AdrenoTools injection for driver: %s", d_name);
    LOGI("  tmp_dir    = %s", tmp_dir_s.c_str());
    LOGI("  hook_dir   = %s", hook_dir_s.c_str());
    LOGI("  driver_dir = %s", driver_dir_s.c_str());

    std::string bait_path = hook_dir_s + "libvulkan_freedreno.so";
    if (access(bait_path.c_str(), F_OK) != 0) {
        LOGE("CRITICAL: The bait file is MISSING from the app's lib folder: %s", bait_path.c_str());
    } else {
        LOGI("Bait file present: %s", bait_path.c_str());
    }

    std::string driver_path = driver_dir_s + d_name;
    if (access(driver_path.c_str(), F_OK) != 0) {
        LOGE("CRITICAL: Custom driver file not found at: %s", driver_path.c_str());
    } else {
        LOGI("Custom driver file present: %s", driver_path.c_str());
    }

    dlerror();

    if (g_hooked_libvulkan != nullptr) {
        LOGI("Driver already hooked once this process; reusing existing handle %p",
             g_hooked_libvulkan);
        env->ReleaseStringUTFChars(tmpLibDir, tmp_dir);
        env->ReleaseStringUTFChars(hookLibDir, hook_dir);
        env->ReleaseStringUTFChars(customDriverDir, driver_dir);
        env->ReleaseStringUTFChars(driverName, d_name);
        return JNI_TRUE;
    }

    // RTLD_LOCAL matches the working adrenotoolstest sample. RTLD_GLOBAL would
    // promote Turnip's symbols into the global namespace and clash with the
    // dlsym lookups libplacebo / mpv perform on the system libvulkan.so.
    void* handle = adrenotools_open_libvulkan(
        RTLD_NOW | RTLD_LOCAL,
        ADRENOTOOLS_DRIVER_CUSTOM,
        tmp_dir_s.c_str(),
        hook_dir_s.c_str(),
        driver_dir_s.c_str(),
        d_name,
        nullptr,
        nullptr
    );

    if (handle == nullptr) {
        const char* err = dlerror();
        LOGE("Driver Hook Failed: Could not load '%s' into memory", d_name);
        LOGE("Reason: %s", err != nullptr ? err : "Android Linker Namespace Bypass Blocked");
    } else {
        g_hooked_libvulkan = handle;
        LOGI("SUCCESS: Custom driver '%s' loaded into memory! Handle: %p", d_name, handle);
    }

    env->ReleaseStringUTFChars(tmpLibDir, tmp_dir);
    env->ReleaseStringUTFChars(hookLibDir, hook_dir);
    env->ReleaseStringUTFChars(customDriverDir, driver_dir);
    env->ReleaseStringUTFChars(driverName, d_name);

    return handle != nullptr ? JNI_TRUE : JNI_FALSE;
}

}

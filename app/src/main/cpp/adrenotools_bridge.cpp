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

extern "C" {

// ==========================================
// NEW EDEN FORK ENV VARIABLE INJECTOR (POSIX)
// ==========================================
JNIEXPORT jboolean JNICALL
Java_app_marlboroadvance_mpvex_system_AdrenoTools_nativeSetEnv(
        JNIEnv *env, jobject thiz, jstring name, jstring value) {
    
    const char *c_name = env->GetStringUTFChars(name, nullptr);
    const char *c_value = env->GetStringUTFChars(value, nullptr);

    // Use standard POSIX setenv to set environment variables natively
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
    
    LOGI("Starting AdrenoTools injection...");

    // 1. Verify the Bait Library actually exists on the phone
    std::string bait_path = std::string(hook_dir) + "/libvulkan_freedreno.so";
    if (access(bait_path.c_str(), F_OK) != 0) {
        LOGE("CRITICAL: The bait file is MISSING from the app's lib folder: %s", bait_path.c_str());
    } else {
        LOGI("SUCCESS: Bait file found at %s", bait_path.c_str());
    }

    // 2. Clear any stale "Ghost" errors left behind by Android
    dlerror(); 

    // 3. Trigger the hack
    void* handle = adrenotools_open_libvulkan(
        RTLD_NOW | RTLD_GLOBAL, 
        ADRENOTOOLS_DRIVER_CUSTOM, 
        tmp_dir, 
        hook_dir, 
        driver_dir, 
        d_name, 
        nullptr, 
        nullptr
    ); 
    
    if (handle == nullptr) {
        const char* err = dlerror();
        LOGE("CRITICAL: adrenotools_open_libvulkan failed!");
        LOGE("Real dlerror: %s", err != nullptr ? err : "No dlerror generated (Likely LinkerNSBypass failed)");
    } else {
        LOGI("SUCCESS: Custom Turnip driver loaded into memory! Handle: %p", handle);
    }

    env->ReleaseStringUTFChars(tmpLibDir, tmp_dir);
    env->ReleaseStringUTFChars(hookLibDir, hook_dir);
    env->ReleaseStringUTFChars(customDriverDir, driver_dir);
    env->ReleaseStringUTFChars(driverName, d_name);

    return handle != nullptr ? JNI_TRUE : JNI_FALSE;
}

}

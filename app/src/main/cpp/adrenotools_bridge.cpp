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
    
    LOGI("Starting AdrenoTools injection for driver: %s", d_name);

    std::string bait_path = std::string(hook_dir) + "/libvulkan_freedreno.so";
    if (access(bait_path.c_str(), F_OK) != 0) {
        LOGE("CRITICAL: The bait file is MISSING from the app's lib folder: %s", bait_path.c_str());
    }

    dlerror(); 

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
    
    // ==========================================
    // UPDATED DYNAMIC LOGGING
    // ==========================================
    if (handle == nullptr) {
        const char* err = dlerror();
        LOGE("Driver Hook Failed: Could not load '%s' into memory", d_name);
        LOGE("Reason: %s", err != nullptr ? err : "Android Linker Namespace Bypass Blocked");
    } else {
        LOGI("SUCCESS: Custom driver '%s' loaded into memory! Handle: %p", d_name, handle);
    }

    env->ReleaseStringUTFChars(tmpLibDir, tmp_dir);
    env->ReleaseStringUTFChars(hookLibDir, hook_dir);
    env->ReleaseStringUTFChars(customDriverDir, driver_dir);
    env->ReleaseStringUTFChars(driverName, d_name);

    return handle != nullptr ? JNI_TRUE : JNI_FALSE;
}

}

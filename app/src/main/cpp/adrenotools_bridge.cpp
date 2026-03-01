#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <android/log.h>
#include "adrenotools/driver.h" 

#define LOG_TAG "AdrenoToolsBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_app_marlboroadvance_mpvex_system_AdrenoTools_nativeHookDriver(
        JNIEnv *env, jobject thiz, jstring tmp_lib_dir, jstring hook_lib_dir, jstring custom_driver_dir, jstring driver_name) {
    
    const char *tmp_dir = env->GetStringUTFChars(tmp_lib_dir, nullptr);
    const char *hook_dir = env->GetStringUTFChars(hook_lib_dir, nullptr);
    const char *driver_dir = env->GetStringUTFChars(custom_driver_dir, nullptr);
    const char *d_name = env->GetStringUTFChars(driver_name, nullptr); 
    
    LOGI("Starting AdrenoTools injection...");
    LOGI("Temp Dir: %s", tmp_dir);
    LOGI("Hook Dir: %s", hook_dir);
    LOGI("Driver Dir: %s", driver_dir);
    LOGI("Driver Name: %s", d_name);

    // Added RTLD_GLOBAL so the driver's symbols become visible to mpv's Vulkan request
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
        // Intercept Android's internal dynamic linker error to see exactly why it failed
        const char* err = dlerror();
        LOGE("CRITICAL: adrenotools_open_libvulkan failed!");
        LOGE("dlerror: %s", err != nullptr ? err : "Unknown linking error");
    } else {
        LOGI("SUCCESS: Custom Turnip driver loaded into memory! Handle: %p", handle);
    }
    
    env->ReleaseStringUTFChars(tmp_lib_dir, tmp_dir);
    env->ReleaseStringUTFChars(hook_lib_dir, hook_dir);
    env->ReleaseStringUTFChars(custom_driver_dir, driver_dir);
    env->ReleaseStringUTFChars(driver_name, d_name);
    
    return handle != nullptr ? JNI_TRUE : JNI_FALSE;
}

}

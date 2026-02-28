#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <android/log.h>
#include "adrenotools/driver.h" 

#define LOG_TAG "AdrenoToolsBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_app_marlboroadvance_mpvex_system_AdrenoTools_nativeHookDriver(
        JNIEnv *env, jobject thiz, jstring tmp_lib_dir, jstring hook_lib_dir, jstring custom_driver_dir) {
    
    const char *tmp_dir = env->GetStringUTFChars(tmp_lib_dir, nullptr);
    const char *hook_dir = env->GetStringUTFChars(hook_lib_dir, nullptr);
    const char *driver_dir = env->GetStringUTFChars(custom_driver_dir, nullptr);
    
    // The REAL adrenotools interception call
    void* handle = adrenotools_open_libvulkan(
        RTLD_NOW, 
        ADRENOTOOLS_DRIVER_CUSTOM, 
        tmp_dir, 
        hook_dir, 
        driver_dir, 
        "libvulkan_freedreno.so", 
        nullptr, 
        nullptr
    ); 
    
    env->ReleaseStringUTFChars(tmp_lib_dir, tmp_dir);
    env->ReleaseStringUTFChars(hook_lib_dir, hook_dir);
    env->ReleaseStringUTFChars(custom_driver_dir, driver_dir);
    
    return handle != nullptr ? JNI_TRUE : JNI_FALSE;
}

}

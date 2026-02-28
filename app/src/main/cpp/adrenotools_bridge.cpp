#include <jni.h>
#include <string>
#include <android/log.h>
#include "adrenotools/driver.h" 

#define LOG_TAG "AdrenoToolsBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_app_marlboroadvance_mpvex_system_AdrenoTools_nativeHookDriver(
        JNIEnv *env, jobject thiz, jstring driver_dir) {
    
    const char *dir_path = env->GetStringUTFChars(driver_dir, nullptr);
    
    // Intercept Vulkan and force it to use our extracted Turnip driver
    void* handle = adrenotools_open("libvulkan_freedreno.so", dir_path); 
    
    env->ReleaseStringUTFChars(driver_dir, dir_path);
    return handle != nullptr ? JNI_TRUE : JNI_FALSE;
}

}


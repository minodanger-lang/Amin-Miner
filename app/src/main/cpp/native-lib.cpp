#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_aminminer_MainActivity_nativeEngineStatus(JNIEnv* env, jobject) {
    return env->NewStringUTF(
        "Mining engine not integrated. No hashing is running."
    );
}

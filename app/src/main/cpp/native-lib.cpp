#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_k7sunny_nexv1_AIManager_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++ (NDK)";
    return env->NewStringUTF(hello.c_str());
}
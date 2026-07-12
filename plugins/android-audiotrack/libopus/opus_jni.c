#include <jni.h>
#include <opus.h>
#include <string.h>

static jlong JNICALL
Java_expo_modules_audiotrack_OpusBridge_nativeEncoderCreate(
    JNIEnv *env, jclass clazz, jint sampleRate, jint channels, jint application)
{
    int err;
    OpusEncoder *enc = opus_encoder_create((int)sampleRate, (int)channels,
                                           (int)application, &err);
    if (err != OPUS_OK) return 0;
    return (jlong)(intptr_t)enc;
}

static jint JNICALL
Java_expo_modules_audiotrack_OpusBridge_nativeEncoderEncode(
    JNIEnv *env, jclass clazz, jlong enc, jbyteArray pcm, jint frameSize, jbyteArray output)
{
    jboolean isCopy;
    jbyte *pcmBytes = (*env)->GetByteArrayElements(env, pcm, &isCopy);
    jbyte *outBytes = (*env)->GetByteArrayElements(env, output, NULL);
    jint outCap = (*env)->GetArrayLength(env, output);

    opus_int16 *pcmSamples = (opus_int16 *)pcmBytes;
    jint result = opus_encode((OpusEncoder *)(intptr_t)enc,
                              pcmSamples, (int)frameSize,
                              (unsigned char *)outBytes, (opus_int32)outCap);

    (*env)->ReleaseByteArrayElements(env, pcm, pcmBytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, output, outBytes, 0);
    return result;
}

static void JNICALL
Java_expo_modules_audiotrack_OpusBridge_nativeEncoderDestroy(
    JNIEnv *env, jclass clazz, jlong enc)
{
    opus_encoder_destroy((OpusEncoder *)(intptr_t)enc);
}

static jlong JNICALL
Java_expo_modules_audiotrack_OpusBridge_nativeDecoderCreate(
    JNIEnv *env, jclass clazz, jint sampleRate, jint channels)
{
    int err;
    OpusDecoder *dec = opus_decoder_create((int)sampleRate, (int)channels, &err);
    if (err != OPUS_OK) return 0;
    return (jlong)(intptr_t)dec;
}

static jint JNICALL
Java_expo_modules_audiotrack_OpusBridge_nativeDecoderDecode(
    JNIEnv *env, jclass clazz, jlong dec, jbyteArray opus, jint opusLen, jbyteArray output)
{
    jboolean isCopy;
    jbyte *opusBytes = (*env)->GetByteArrayElements(env, opus, &isCopy);
    jbyte *outBytes = (*env)->GetByteArrayElements(env, output, NULL);
    jint outCap = (*env)->GetArrayLength(env, output);

    opus_int16 *pcmSamples = (opus_int16 *)outBytes;
    jint result = opus_decode((OpusDecoder *)(intptr_t)dec,
                              (unsigned char *)opusBytes, (opus_int32)opusLen,
                              pcmSamples, outCap / 2, 0);

    (*env)->ReleaseByteArrayElements(env, opus, opusBytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, output, outBytes, 0);
    return result >= 0 ? result * 2 : result;
}

static void JNICALL
Java_expo_modules_audiotrack_OpusBridge_nativeDecoderDestroy(
    JNIEnv *env, jclass clazz, jlong dec)
{
    opus_decoder_destroy((OpusDecoder *)(intptr_t)dec);
}

static JNINativeMethod methods[] = {
    {"nativeEncoderCreate",  "(III)J",                                   (void *)Java_expo_modules_audiotrack_OpusBridge_nativeEncoderCreate},
    {"nativeEncoderEncode",  "(J[BI[B)I",                              (void *)Java_expo_modules_audiotrack_OpusBridge_nativeEncoderEncode},
    {"nativeEncoderDestroy", "(J)V",                                     (void *)Java_expo_modules_audiotrack_OpusBridge_nativeEncoderDestroy},
    {"nativeDecoderCreate",  "(II)J",                                    (void *)Java_expo_modules_audiotrack_OpusBridge_nativeDecoderCreate},
    {"nativeDecoderDecode",  "(J[BI[B)I",                                (void *)Java_expo_modules_audiotrack_OpusBridge_nativeDecoderDecode},
    {"nativeDecoderDestroy", "(J)V",                                     (void *)Java_expo_modules_audiotrack_OpusBridge_nativeDecoderDestroy},
};

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;

    jclass clazz = (*env)->FindClass(env, "expo/modules/audiotrack/OpusBridge");
    if (!clazz) return JNI_ERR;

    if ((*env)->RegisterNatives(env, clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0)
        return JNI_ERR;

    return JNI_VERSION_1_6;
}

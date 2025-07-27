#define DR_MP3_IMPLEMENTATION
#include "dr_mp3.h"

#define STB_VORBIS_IMPLEMENTATION
#include "stb_vorbis.c"

#include "org_academy_api_client_jni_AudioDecoderJNI.h"
#include <stdlib.h>
#include <string.h>

static int is_ogg(const unsigned char* data, size_t dataSize) {
    return dataSize > 4 && data[0] == 'O' && data[1] == 'g' && data[2] == 'g' && data[3] == 'S';
}

typedef enum {
    DECODER_TYPE_NONE,
    DECODER_TYPE_MP3,
    DECODER_TYPE_OGG
} DecoderType;

typedef struct {
    DecoderType type;
    void* pDecoder;
    int channels;
    int sample_rate;
} GenericDecoder;

JNIEXPORT jlong JNICALL Java_org_academy_api_client_jni_AudioDecoderJNI_decoder_1init_1memory
  (JNIEnv *env, jclass clazz, jobject dataBuffer) {
    unsigned char* audioData = (unsigned char*)(*env)->GetDirectBufferAddress(env, dataBuffer);
    jlong dataSize = (*env)->GetDirectBufferCapacity(env, dataBuffer);

    if (!audioData || dataSize <= 0) {
        return 0;
    }

    GenericDecoder* pWrapper = (GenericDecoder*)calloc(1, sizeof(GenericDecoder));
    if (!pWrapper) {
        return 0;
    }

    if (is_ogg(audioData, (size_t)dataSize)) {
        int error;
        stb_vorbis* pVorbis = stb_vorbis_open_memory(audioData, (int)dataSize, &error, NULL);
        if (pVorbis) {
            pWrapper->type = DECODER_TYPE_OGG;
            pWrapper->pDecoder = pVorbis;
            stb_vorbis_info info = stb_vorbis_get_info(pVorbis);
            pWrapper->channels = info.channels;
            pWrapper->sample_rate = info.sample_rate;
        } else {
            free(pWrapper);
            pWrapper = NULL;
        }
    } else {
        drmp3* pMp3 = (drmp3*)calloc(1, sizeof(drmp3));
        if (pMp3 && drmp3_init_memory(pMp3, audioData, (size_t)dataSize, NULL)) {
            pWrapper->type = DECODER_TYPE_MP3;
            pWrapper->pDecoder = pMp3;
            pWrapper->channels = pMp3->channels;
            pWrapper->sample_rate = pMp3->sampleRate;
        } else {
            if (pMp3) free(pMp3);
            free(pWrapper);
            pWrapper = NULL;
        }
    }

    return (jlong)pWrapper;
}

JNIEXPORT jlong JNICALL Java_org_academy_api_client_jni_AudioDecoderJNI_decoder_1read_1pcm_1frames
  (JNIEnv *env, jclass clazz, jlong decoderPtr, jobject pcmBuffer, jint framesToRead) {
    GenericDecoder* pWrapper = (GenericDecoder*)decoderPtr;
    if (!pWrapper || !pWrapper->pDecoder || pWrapper->channels <= 0) {
        return 0;
    }

    short* directBuffer = (short*)(*env)->GetDirectBufferAddress(env, pcmBuffer);
    if (!directBuffer) {
        return 0;
    }

    jlong capacityInBytes = (*env)->GetDirectBufferCapacity(env, pcmBuffer);
    long capacityInFrames = capacityInBytes / sizeof(short) / pWrapper->channels;

    long actualFramesToRead = framesToRead < capacityInFrames ? framesToRead : capacityInFrames;

    long long framesRead = 0;
    switch (pWrapper->type) {
        case DECODER_TYPE_MP3:
            framesRead = (long long)drmp3_read_pcm_frames_s16((drmp3*)pWrapper->pDecoder, (drmp3_uint64)actualFramesToRead, directBuffer);
            break;
        case DECODER_TYPE_OGG: {
            int samplesToRead = (int)actualFramesToRead * pWrapper->channels;
            int samplesRead = stb_vorbis_get_samples_short_interleaved((stb_vorbis*)pWrapper->pDecoder, pWrapper->channels, directBuffer, samplesToRead);
            framesRead = samplesRead;
            break;
        }
        default:
            break;
    }

    return (jlong)framesRead;
}

JNIEXPORT void JNICALL Java_org_academy_api_client_jni_AudioDecoderJNI_decoder_1uninit
  (JNIEnv *env, jclass clazz, jlong decoderPtr) {
    GenericDecoder* pWrapper = (GenericDecoder*)decoderPtr;
    if (pWrapper) {
        if (pWrapper->pDecoder) {
            switch (pWrapper->type) {
                case DECODER_TYPE_MP3:
                    drmp3_uninit((drmp3*)pWrapper->pDecoder);
                    free(pWrapper->pDecoder);
                    break;
                case DECODER_TYPE_OGG:
                    stb_vorbis_close((stb_vorbis*)pWrapper->pDecoder);
                    break;
                default: break;
            }
        }
        free(pWrapper);
    }
}

JNIEXPORT jint JNICALL Java_org_academy_api_client_jni_AudioDecoderJNI_decoder_1get_1channels
  (JNIEnv *env, jclass clazz, jlong decoderPtr) {
    GenericDecoder* pWrapper = (GenericDecoder*)decoderPtr;
    if (!pWrapper) return 0;
    return pWrapper->channels;
}

JNIEXPORT jint JNICALL Java_org_academy_api_client_jni_AudioDecoderJNI_decoder_1get_1sample_1rate
  (JNIEnv *env, jclass clazz, jlong decoderPtr) {
    GenericDecoder* pWrapper = (GenericDecoder*)decoderPtr;
    if (!pWrapper) return 0;
    return pWrapper->sample_rate;
}

JNIEXPORT jlong JNICALL Java_org_academy_api_client_jni_AudioDecoderJNI_decoder_1get_1length_1in_1pcm_1frames
  (JNIEnv *env, jclass clazz, jlong decoderPtr) {
    GenericDecoder* pWrapper = (GenericDecoder*)decoderPtr;
    if (!pWrapper || !pWrapper->pDecoder) return 0;
    switch (pWrapper->type) {
        case DECODER_TYPE_MP3: {
            drmp3_uint64 length = drmp3_get_pcm_frame_count((drmp3*)pWrapper->pDecoder);
            return (jlong)length;
        }
        case DECODER_TYPE_OGG: {
            if (pWrapper->channels == 0) return 0;
            unsigned int length_in_samples = stb_vorbis_stream_length_in_samples((stb_vorbis*)pWrapper->pDecoder);
            return (jlong)length_in_samples;
        }
        default: return 0;
    }
}

JNIEXPORT jint JNICALL Java_org_academy_api_client_jni_AudioDecoderJNI_decoder_1seek_1to_1pcm_1frame
  (JNIEnv *env, jclass clazz, jlong decoderPtr, jlong frameIndex) {
    GenericDecoder* pWrapper = (GenericDecoder*)decoderPtr;
    if (!pWrapper || !pWrapper->pDecoder) return -1;
    switch (pWrapper->type) {
        case DECODER_TYPE_MP3:
            return drmp3_seek_to_pcm_frame((drmp3*)pWrapper->pDecoder, (drmp3_uint64)frameIndex) ? 0 : -1;
        case DECODER_TYPE_OGG:
            return stb_vorbis_seek_frame((stb_vorbis*)pWrapper->pDecoder, (unsigned int)frameIndex) ? 0 : -1;
        default: return -1;
    }
}
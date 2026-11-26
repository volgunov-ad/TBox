// Camera frame texture backed by an AHardwareBuffer: the CPU copies the UYVY slot into the
// buffer and the GPU samples it in place, instead of glTexSubImage2D copying it in the driver.
// All calls run on the GL thread that owns the current EGL context.
#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>

#define TAG "CamdAhb"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

// The GPU finishes one frame in a few ms; a longer wait means a stuck pipeline.
constexpr EGLTimeKHR kFenceTimeoutNs = 100ull * 1000 * 1000;

struct Procs {
    PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC getClientBuffer = nullptr;
    PFNEGLCREATEIMAGEKHRPROC createImage = nullptr;
    PFNEGLDESTROYIMAGEKHRPROC destroyImage = nullptr;
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC targetTexture = nullptr;
    PFNEGLCREATESYNCKHRPROC createSync = nullptr;
    PFNEGLDESTROYSYNCKHRPROC destroySync = nullptr;
    PFNEGLCLIENTWAITSYNCKHRPROC clientWait = nullptr;

    bool load() {
        if (getClientBuffer) return true;
        getClientBuffer = reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(
            eglGetProcAddress("eglGetNativeClientBufferANDROID"));
        createImage = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(eglGetProcAddress("eglCreateImageKHR"));
        destroyImage = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(eglGetProcAddress("eglDestroyImageKHR"));
        targetTexture = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
            eglGetProcAddress("glEGLImageTargetTexture2DOES"));
        createSync = reinterpret_cast<PFNEGLCREATESYNCKHRPROC>(eglGetProcAddress("eglCreateSyncKHR"));
        destroySync = reinterpret_cast<PFNEGLDESTROYSYNCKHRPROC>(eglGetProcAddress("eglDestroySyncKHR"));
        clientWait = reinterpret_cast<PFNEGLCLIENTWAITSYNCKHRPROC>(eglGetProcAddress("eglClientWaitSyncKHR"));
        bool ok = getClientBuffer && createImage && destroyImage && targetTexture && createSync &&
                  destroySync && clientWait;
        if (!ok) getClientBuffer = nullptr;
        return ok;
    }
};

Procs procs;

struct FrameBuffer {
    AHardwareBuffer* buffer = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLImageKHR image = EGL_NO_IMAGE_KHR;
    EGLSyncKHR fence = EGL_NO_SYNC_KHR;
    uint32_t texels = 0;  // width / 2: one RGBA texel per two UYVY pixels
    uint32_t rows = 0;
    uint32_t stride = 0;
};

// Returns false when the GPU did not release the buffer in time.
bool waitFence(FrameBuffer* fb) {
    if (fb->fence == EGL_NO_SYNC_KHR) return true;
    EGLint rc = procs.clientWait(fb->display, fb->fence, EGL_SYNC_FLUSH_COMMANDS_BIT_KHR, kFenceTimeoutNs);
    procs.destroySync(fb->display, fb->fence);
    fb->fence = EGL_NO_SYNC_KHR;
    return rc == EGL_CONDITION_SATISFIED_KHR;
}

void destroy(FrameBuffer* fb) {
    waitFence(fb);
    if (fb->image != EGL_NO_IMAGE_KHR) procs.destroyImage(fb->display, fb->image);
    if (fb->buffer) AHardwareBuffer_release(fb->buffer);
    delete fb;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_vad_dashing_camd_AhbFrameTexture_nativeCreate(JNIEnv*, jclass, jint width, jint height, jint texture) {
    if (width <= 0 || height <= 0 || (width & 1) != 0) return 0;
    if (!procs.load()) {
        ALOGI("нет функций EGLImage / fence");
        return 0;
    }
    auto* fb = new FrameBuffer();
    fb->display = eglGetCurrentDisplay();
    AHardwareBuffer_Desc desc{};
    desc.width = (uint32_t)width / 2;
    desc.height = (uint32_t)height;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    int rc = AHardwareBuffer_allocate(&desc, &fb->buffer);
    if (rc != 0 || !fb->buffer || fb->display == EGL_NO_DISPLAY) {
        ALOGI("AHardwareBuffer %dx%d не выделен: %d", width / 2, height, rc);
        fb->buffer = nullptr;
        destroy(fb);
        return 0;
    }
    AHardwareBuffer_describe(fb->buffer, &desc);
    fb->texels = desc.width;
    fb->rows = desc.height;
    fb->stride = desc.stride;
    EGLClientBuffer client = procs.getClientBuffer(fb->buffer);
    EGLint attrs[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
    fb->image = client ? procs.createImage(fb->display, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, client, attrs)
                       : EGL_NO_IMAGE_KHR;
    if (fb->image == EGL_NO_IMAGE_KHR) {
        ALOGI("EGLImage не создан: 0x%x", eglGetError());
        destroy(fb);
        return 0;
    }
    while (glGetError() != GL_NO_ERROR) {
    }
    glBindTexture(GL_TEXTURE_2D, (GLuint)texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    procs.targetTexture(GL_TEXTURE_2D, (GLeglImageOES)fb->image);
    GLenum err = glGetError();
    if (err != GL_NO_ERROR) {
        ALOGI("EGLImage не привязан к текстуре: 0x%x", err);
        destroy(fb);
        return 0;
    }
    ALOGI("общий буфер %ux%u stride %u", fb->texels, fb->rows, fb->stride);
    return reinterpret_cast<jlong>(fb);
}

// Copies [bytes] from [source] (a direct buffer, e.g. the grabber mapping) at [offset].
extern "C" JNIEXPORT jboolean JNICALL
Java_vad_dashing_camd_AhbFrameTexture_nativeWrite(JNIEnv* env, jclass, jlong handle, jobject source,
                                                  jint offset, jint bytes) {
    auto* fb = reinterpret_cast<FrameBuffer*>(handle);
    if (!fb || offset < 0) return JNI_FALSE;
    const size_t row = (size_t)fb->texels * 4;
    if ((size_t)bytes != row * fb->rows) return JNI_FALSE;
    auto* base = static_cast<const uint8_t*>(env->GetDirectBufferAddress(source));
    jlong capacity = env->GetDirectBufferCapacity(source);
    if (!base || capacity < (jlong)offset + bytes) return JNI_FALSE;
    if (!waitFence(fb)) {
        ALOGI("видеокарта не освободила буфер за 100 мс");
        return JNI_FALSE;
    }
    void* dst = nullptr;
    if (AHardwareBuffer_lock(fb->buffer, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr, &dst) != 0 || !dst) {
        return JNI_FALSE;
    }
    const uint8_t* src = base + offset;
    if (fb->stride == fb->texels) {
        memcpy(dst, src, (size_t)bytes);
    } else {
        for (uint32_t y = 0; y < fb->rows; ++y) {
            memcpy(static_cast<uint8_t*>(dst) + (size_t)y * fb->stride * 4, src + y * row, row);
        }
    }
    AHardwareBuffer_unlock(fb->buffer, nullptr);
    return JNI_TRUE;
}

// After the draw that samples the buffer: the next write waits for this point.
extern "C" JNIEXPORT void JNICALL
Java_vad_dashing_camd_AhbFrameTexture_nativeFence(JNIEnv*, jclass, jlong handle) {
    auto* fb = reinterpret_cast<FrameBuffer*>(handle);
    if (!fb) return;
    if (fb->fence != EGL_NO_SYNC_KHR) procs.destroySync(fb->display, fb->fence);
    fb->fence = procs.createSync(fb->display, EGL_SYNC_FENCE_KHR, nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_vad_dashing_camd_AhbFrameTexture_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    auto* fb = reinterpret_cast<FrameBuffer*>(handle);
    if (fb) destroy(fb);
}

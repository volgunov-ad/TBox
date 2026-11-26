// Diagnostic: CPU cost of handing a camera frame to the GPU, current glTexSubImage2D path
// against an AHardwareBuffer bound as an EGLImage (the GPU samples it in place).
#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <time.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#define TAG "CamdGpuProbe"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

double now_ms(clockid_t clock) {
    timespec ts{};
    clock_gettime(clock, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1e6;
}

bool has_ext(const char* list, const char* name) {
    if (!list) return false;
    size_t n = strlen(name);
    for (const char* p = list; (p = strstr(p, name)) != nullptr; p += n) {
        if ((p == list || p[-1] == ' ') && (p[n] == ' ' || p[n] == '\0')) return true;
    }
    return false;
}

GLuint compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    return s;
}

const char* kVertex =
    "attribute vec2 aPos; varying vec2 vUv;"
    "void main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }";
// Same work per pixel as the UYVY shader: one texel fetch, YUV to RGB.
const char* kFragment =
    "precision mediump float; varying vec2 vUv; uniform sampler2D uTex;"
    "void main(){ vec4 p = texture2D(uTex, vUv); float y = p.g - 0.0625; float u = p.r - 0.5; float v = p.b - 0.5;"
    " gl_FragColor = vec4(y + 1.596 * v, y - 0.391 * u - 0.813 * v, y + 2.018 * u, 1.0); }";

struct Timing {
    double process_ms = 0;
    double thread_ms = 0;
    double wall_ms = 0;
    double copy_ms = 0;
    int frames = 0;
};

std::string line(const char* name, const Timing& t) {
    char buf[256];
    if (t.frames <= 0) {
        snprintf(buf, sizeof buf, "%s: не измерено\n", name);
    } else {
        snprintf(buf, sizeof buf,
                 "%s: процесс %.2f мс, поток %.2f мс, время %.2f мс на кадр\n",
                 name, t.process_ms / t.frames, t.thread_ms / t.frames, t.wall_ms / t.frames);
    }
    std::string out = buf;
    if (t.copy_ms > 0 && t.frames > 0) {
        snprintf(buf, sizeof buf, "  из них запись в буфер (lock+memcpy+unlock) %.2f мс\n", t.copy_ms / t.frames);
        out += buf;
    }
    return out;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_vad_dashing_camd_GpuUploadProbe_nativeRun(JNIEnv* env, jclass, jint width, jint height, jint frames) {
    std::string out;
    char buf[512];
    const int texW = width / 2;  // UYVY as RGBA: one texel per two pixels
    const int texH = height;
    const size_t bytes = (size_t)width * height * 2;

    EGLDisplay dpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    // No eglTerminate at the end: camera windows share this process-wide display.
    if (dpy == EGL_NO_DISPLAY || !eglInitialize(dpy, nullptr, nullptr)) {
        return env->NewStringUTF("EGL: нет дисплея");
    }
    const char* eglExt = eglQueryString(dpy, EGL_EXTENSIONS);
    EGLint cfgAttr[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig cfg;
    EGLint n = 0;
    eglChooseConfig(dpy, cfgAttr, &cfg, 1, &n);
    EGLint ctxAttr[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext ctx = n > 0 ? eglCreateContext(dpy, cfg, EGL_NO_CONTEXT, ctxAttr) : EGL_NO_CONTEXT;
    EGLint pbAttr[] = {EGL_WIDTH, width, EGL_HEIGHT, height, EGL_NONE};
    EGLSurface pb = ctx != EGL_NO_CONTEXT ? eglCreatePbufferSurface(dpy, cfg, pbAttr) : EGL_NO_SURFACE;
    if (pb == EGL_NO_SURFACE || !eglMakeCurrent(dpy, pb, pb, ctx)) {
        if (ctx != EGL_NO_CONTEXT) eglDestroyContext(dpy, ctx);
        return env->NewStringUTF("EGL: не удалось создать контекст");
    }
    const char* glExt = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    snprintf(buf, sizeof buf, "GPU: %s, %s\n", glGetString(GL_RENDERER), glGetString(GL_VERSION));
    out += buf;
    const char* names[] = {"EGL_KHR_image_base", "EGL_ANDROID_image_native_buffer",
                           "EGL_ANDROID_get_native_client_buffer", "EGL_EXT_image_dma_buf_import",
                           "EGL_KHR_fence_sync", "EGL_ANDROID_native_fence_sync"};
    for (const char* name : names) {
        snprintf(buf, sizeof buf, "%s: %s\n", name, has_ext(eglExt, name) ? "есть" : "нет");
        out += buf;
    }
    const char* glNames[] = {"GL_OES_EGL_image", "GL_OES_EGL_image_external"};
    for (const char* name : glNames) {
        snprintf(buf, sizeof buf, "%s: %s\n", name, has_ext(glExt, name) ? "есть" : "нет");
        out += buf;
    }

    GLuint prog = glCreateProgram();
    glAttachShader(prog, compile(GL_VERTEX_SHADER, kVertex));
    glAttachShader(prog, compile(GL_FRAGMENT_SHADER, kFragment));
    glBindAttribLocation(prog, 0, "aPos");
    glLinkProgram(prog);
    glUseProgram(prog);
    glUniform1i(glGetUniformLocation(prog, "uTex"), 0);
    const GLfloat quad[] = {-1, -1, 1, -1, -1, 1, 1, 1};
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, quad);
    glEnableVertexAttribArray(0);
    glViewport(0, 0, width, height);

    std::vector<uint8_t> src(bytes);
    for (size_t i = 0; i < bytes; ++i) src[i] = (uint8_t)(i * 31u);

    auto measure = [&](auto&& frame, Timing& t) {
        frame(0);  // warm-up: allocations, shader compile
        glFinish();
        double p0 = now_ms(CLOCK_PROCESS_CPUTIME_ID), t0 = now_ms(CLOCK_THREAD_CPUTIME_ID), w0 = now_ms(CLOCK_MONOTONIC);
        for (int i = 1; i <= frames; ++i) {
            frame(i);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            glFinish();
        }
        t.process_ms = now_ms(CLOCK_PROCESS_CPUTIME_ID) - p0;
        t.thread_ms = now_ms(CLOCK_THREAD_CPUTIME_ID) - t0;
        t.wall_ms = now_ms(CLOCK_MONOTONIC) - w0;
        t.frames = frames;
    };

    // A: current path, the driver copies 2 MB from process memory on every frame.
    GLuint tex[2];
    glGenTextures(2, tex);
    glBindTexture(GL_TEXTURE_2D, tex[0]);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, texW, texH, 0, GL_RGBA, GL_UNSIGNED_BYTE, src.data());
    Timing texSub;
    measure([&](int i) {
        src[(size_t)i % bytes] ^= 1;
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, texW, texH, GL_RGBA, GL_UNSIGNED_BYTE, src.data());
    }, texSub);
    Timing drawOnly;
    measure([&](int) {}, drawOnly);
    out += line("A glTexSubImage2D (сейчас)", texSub);
    out += line("0 только отрисовка, без передачи", drawOnly);

    // B: AHardwareBuffer bound as EGLImage; the writer fills it, the GPU reads it in place.
    auto getClientBuffer = reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(
        eglGetProcAddress("eglGetNativeClientBufferANDROID"));
    auto createImage = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(eglGetProcAddress("eglCreateImageKHR"));
    auto destroyImage = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(eglGetProcAddress("eglDestroyImageKHR"));
    auto targetTexture = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
        eglGetProcAddress("glEGLImageTargetTexture2DOES"));
    AHardwareBuffer_Desc desc{};
    desc.width = (uint32_t)texW;
    desc.height = (uint32_t)texH;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
    AHardwareBuffer* ahb = nullptr;
    int allocRc = AHardwareBuffer_allocate(&desc, &ahb);
    EGLImageKHR image = EGL_NO_IMAGE_KHR;
    if (allocRc != 0 || !ahb) {
        snprintf(buf, sizeof buf, "B AHardwareBuffer: выделить не удалось (%d)\n", allocRc);
        out += buf;
    } else if (!getClientBuffer || !createImage || !targetTexture) {
        out += "B AHardwareBuffer: нет функций EGLImage\n";
    } else {
        AHardwareBuffer_describe(ahb, &desc);
        EGLClientBuffer client = getClientBuffer(ahb);
        EGLint imgAttr[] = {EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE};
        image = client ? createImage(dpy, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, client, imgAttr) : EGL_NO_IMAGE_KHR;
        if (image == EGL_NO_IMAGE_KHR) {
            snprintf(buf, sizeof buf, "B AHardwareBuffer: EGLImage не создан (0x%x)\n", eglGetError());
            out += buf;
        } else {
            glBindTexture(GL_TEXTURE_2D, tex[1]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            targetTexture(GL_TEXTURE_2D, (GLeglImageOES)image);
            GLenum err = glGetError();
            snprintf(buf, sizeof buf, "B AHardwareBuffer: %ux%u, stride %u, EGLImage %s\n",
                     desc.width, desc.height, desc.stride, err == GL_NO_ERROR ? "привязан" : "ошибка привязки");
            out += buf;
            Timing withWrite;
            const size_t row = (size_t)texW * 4;
            int lockFailures = 0;
            measure([&](int i) {
                double c0 = now_ms(CLOCK_THREAD_CPUTIME_ID);
                void* dst = nullptr;
                if (AHardwareBuffer_lock(ahb, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr, &dst) != 0 || !dst) {
                    ++lockFailures;
                    return;
                }
                src[(size_t)i % bytes] ^= 1;
                if (desc.stride == (uint32_t)texW) {
                    memcpy(dst, src.data(), bytes);
                } else {
                    for (int y = 0; y < texH; ++y) {
                        memcpy(static_cast<uint8_t*>(dst) + (size_t)y * desc.stride * 4, src.data() + y * row, row);
                    }
                }
                AHardwareBuffer_unlock(ahb, nullptr);
                withWrite.copy_ms += now_ms(CLOCK_THREAD_CPUTIME_ID) - c0;
            }, withWrite);
            Timing sampleOnly;
            measure([&](int) {}, sampleOnly);
            out += line("B запись в AHardwareBuffer + отрисовка", withWrite);
            out += line("B только отрисовка из AHardwareBuffer", sampleOnly);
            if (lockFailures > 0) {
                snprintf(buf, sizeof buf, "  lock не удался %d раз\n", lockFailures);
                out += buf;
            }
        }
    }

    if (image != EGL_NO_IMAGE_KHR && destroyImage) destroyImage(dpy, image);
    if (ahb) AHardwareBuffer_release(ahb);
    glDeleteTextures(2, tex);
    glDeleteProgram(prog);
    eglMakeCurrent(dpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(dpy, pb);
    eglDestroyContext(dpy, ctx);
    ALOGI("%s", out.c_str());
    return env->NewStringUTF(out.c_str());
}

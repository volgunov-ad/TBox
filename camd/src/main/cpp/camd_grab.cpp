#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "CamdQcar"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define QCARCAM_MAX_NUM_PLANES 3
#define QCARCAM_FMT_UYVY_8 0x07080102u
#define QCARCAM_VERSION 0x00000404u
#define FRAME_TIMEOUT_NS 15000000ull
#define MAX_CAMERAS 8

#define DESIRED "/data/data/vad.dashing.camd/files/grab.desired"
#define BEAT "/data/local/tmp/camd-grab.beat"
#define FRAME_RAM "/mnt/camd-ram"
#define UYVY_MAGIC 0x31765955u
#define UYVY_HEADER 64

typedef void* qcarcam_hndl_t;
typedef int qcarcam_ret_t;

typedef struct {
    unsigned int flags;
    unsigned int version;
    const char* debug_tag;
    unsigned char pad[64];
} qcarcam_init_t;

typedef struct {
    unsigned int width;
    unsigned int height;
    unsigned int stride;
    unsigned int size;
    void* p_buf;
} qcarcam_plane_t;

typedef struct {
    qcarcam_plane_t planes[QCARCAM_MAX_NUM_PLANES];
    unsigned int n_planes;
} qcarcam_buffer_t;

typedef struct {
    unsigned int color_fmt;
    qcarcam_buffer_t* buffers;
    unsigned int n_buffers;
    unsigned int flags;
} qcarcam_buffers_t;

typedef struct {
    unsigned int idx;
    unsigned int flags;
    unsigned int seq_no;
    unsigned long long timestamp;
    unsigned long long timestamp_system;
    unsigned long long sof_qtimestamp;
    unsigned char pad[64];
} qcarcam_frame_info_t;

typedef qcarcam_ret_t (*fn_initialize)(qcarcam_init_t*);
typedef qcarcam_hndl_t (*fn_open)(unsigned int);
typedef qcarcam_ret_t (*fn_close)(qcarcam_hndl_t);
typedef qcarcam_ret_t (*fn_s_buffers)(qcarcam_hndl_t, qcarcam_buffers_t*);
typedef qcarcam_ret_t (*fn_start)(qcarcam_hndl_t);
typedef qcarcam_ret_t (*fn_stop)(qcarcam_hndl_t);
typedef qcarcam_ret_t (*fn_get_frame)(qcarcam_hndl_t, qcarcam_frame_info_t*, unsigned long long, unsigned int);
typedef qcarcam_ret_t (*fn_release_frame)(qcarcam_hndl_t, unsigned int);

struct IonNew {
    uint64_t len;
    uint32_t heap_id_mask;
    uint32_t flags;
    uint32_t fd;
    uint32_t unused;
};
#define ION_IOC_ALLOC_NEW _IOWR('I', 0, struct IonNew)

struct Camera {
    int id;
    int width;
    int height;
    int stride;
    int size;
    int seq;
    qcarcam_hndl_t handle;
    int started;
    int fds[5];
    void* ptrs[5];
    qcarcam_buffer_t buffers[5];
    qcarcam_buffers_t list;
    void* shared;
    int sharedBytes;
};

static fn_initialize p_initialize = nullptr;
static fn_open p_open = nullptr;
static fn_close p_close = nullptr;
static fn_s_buffers p_s_buffers = nullptr;
static fn_start p_start = nullptr;
static fn_stop p_stop = nullptr;
static fn_get_frame p_get_frame = nullptr;
static fn_release_frame p_release_frame = nullptr;

static Camera* open_cameras[10];
static long long next_try[10];

static int map_shared(Camera* camera);

static int camera_size(int id, int* width, int* height) {
    if (id == 0) { *width = 1488; *height = 616; return 1; }
    if (id == 1) { *width = 1280; *height = 720; return 1; }
    if (id == 3 || id == 4 || id == 5 || id == 8 || id == 9) { *width = 1280; *height = 800; return 1; }
    return 0;
}

static int alloc_dma(int ion, int bytes) {
    static const uint32_t heaps[] = { 1u << 25, 1u, 1u << 4, 1u << 1, 1u << 26, 1u << 27 };
    for (unsigned i = 0; i < sizeof(heaps) / sizeof(heaps[0]); i++) {
        struct IonNew data;
        memset(&data, 0, sizeof(data));
        data.len = (uint64_t)bytes;
        data.heap_id_mask = heaps[i];
        if (ioctl(ion, ION_IOC_ALLOC_NEW, &data) == 0 && (int)data.fd >= 0) {
            fprintf(stderr, "ion heap 0x%x fd %d\n", heaps[i], (int)data.fd);
            return (int)data.fd;
        }
    }
    fprintf(stderr, "ion alloc errno=%d\n", errno);
    return -1;
}

static void release_camera(Camera* camera) {
    if (!camera) return;
    if (camera->started && camera->handle && p_stop) p_stop(camera->handle);
    if (camera->handle && p_close) p_close(camera->handle);
    for (int i = 0; i < 5; i++) {
        if (camera->ptrs[i] && camera->ptrs[i] != MAP_FAILED) munmap(camera->ptrs[i], camera->size);
        if (camera->fds[i] > 0) close(camera->fds[i]);
    }
    if (camera->shared && camera->shared != MAP_FAILED) {
        munmap(camera->shared, (size_t)camera->sharedBytes);
    }
    char path[128];
    snprintf(path, sizeof(path), FRAME_RAM "/camd-uyvy-%d", camera->id);
    unlink(path);
    snprintf(path, sizeof(path), "/data/local/tmp/camd-uyvy-%d", camera->id);
    unlink(path);
    snprintf(path, sizeof(path), "/data/local/tmp/camd-frame-%d.bin", camera->id);
    unlink(path);
    free(camera);
}

static int init_client(void) {
    void* library = dlopen("/system/lib64/libais_client.so", RTLD_NOW);
    if (!library) {
        fprintf(stderr, "dlopen libais_client.so: %s\n", dlerror());
        return -1;
    }
    p_initialize = (fn_initialize)dlsym(library, "qcarcam_initialize");
    p_open = (fn_open)dlsym(library, "qcarcam_open");
    p_close = (fn_close)dlsym(library, "qcarcam_close");
    p_s_buffers = (fn_s_buffers)dlsym(library, "qcarcam_s_buffers");
    p_start = (fn_start)dlsym(library, "qcarcam_start");
    p_stop = (fn_stop)dlsym(library, "qcarcam_stop");
    p_get_frame = (fn_get_frame)dlsym(library, "qcarcam_get_frame");
    p_release_frame = (fn_release_frame)dlsym(library, "qcarcam_release_frame");
    if (!p_initialize || !p_open || !p_s_buffers || !p_start || !p_get_frame || !p_release_frame) {
        fprintf(stderr, "qcarcam symbols missing\n");
        return -2;
    }
    qcarcam_init_t init;
    memset(&init, 0, sizeof(init));
    init.version = QCARCAM_VERSION;
    init.debug_tag = "camd";
    int ret = p_initialize(&init);
    fprintf(stderr, "qcarcam_initialize -> %d\n", ret);
    ALOGI("qcarcam_initialize -> %d", ret);
    return ret;
}

static Camera* open_camera(int id) {
    int width = 0;
    int height = 0;
    if (!camera_size(id, &width, &height)) return nullptr;
    int ion = open("/dev/ion", O_RDONLY | O_CLOEXEC);
    if (ion < 0) {
        fprintf(stderr, "open /dev/ion errno=%d\n", errno);
        return nullptr;
    }
    Camera* camera = (Camera*)calloc(1, sizeof(Camera));
    camera->id = id;
    camera->width = width;
    camera->height = height;
    camera->stride = width * 2;
    camera->size = camera->stride * height;
    for (int i = 0; i < 5; i++) {
        int fd = alloc_dma(ion, camera->size);
        camera->fds[i] = fd;
        if (fd < 0) {
            close(ion);
            release_camera(camera);
            return nullptr;
        }
        camera->ptrs[i] = mmap(nullptr, camera->size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        camera->buffers[i].n_planes = 1;
        camera->buffers[i].planes[0].width = (unsigned)width;
        camera->buffers[i].planes[0].height = (unsigned)height;
        camera->buffers[i].planes[0].stride = (unsigned)camera->stride;
        camera->buffers[i].planes[0].size = (unsigned)camera->size;
        camera->buffers[i].planes[0].p_buf = (void*)(uintptr_t)fd;
    }
    close(ion);
    camera->list.color_fmt = QCARCAM_FMT_UYVY_8;
    camera->list.buffers = camera->buffers;
    camera->list.n_buffers = 5;
    camera->handle = p_open((unsigned)id);
    if (!camera->handle) {
        fprintf(stderr, "qcarcam_open %d failed\n", id);
        release_camera(camera);
        return nullptr;
    }
    int buffers = p_s_buffers(camera->handle, &camera->list);
    fprintf(stderr, "qcarcam_s_buffers %d -> %d\n", id, buffers);
    if (buffers != 0) {
        release_camera(camera);
        return nullptr;
    }
    int started = p_start(camera->handle);
    fprintf(stderr, "qcarcam_start %d -> %d\n", id, started);
    if (started != 0) {
        release_camera(camera);
        return nullptr;
    }
    camera->started = 1;
    if (map_shared(camera) != 0) {
        fprintf(stderr, "uyvy map %d failed errno=%d\n", id, errno);
        release_camera(camera);
        return nullptr;
    }
    return camera;
}

static int map_shared(Camera* camera) {
    char path[128];
    snprintf(path, sizeof(path), FRAME_RAM "/camd-uyvy-%d", camera->id);
    int mapSize = UYVY_HEADER + camera->size * 2;
    int fd = open(path, O_RDWR | O_CLOEXEC);
    if (fd >= 0) {
        struct stat st;
        if (fstat(fd, &st) != 0 || st.st_size != mapSize) {
            close(fd);
            unlink(path);
            fd = -1;
        }
    }
    if (fd < 0) {
        fd = open(path, O_RDWR | O_CREAT | O_EXCL | O_CLOEXEC, 0666);
        if (fd < 0) return -1;
        if (ftruncate(fd, mapSize) != 0) {
            close(fd);
            unlink(path);
            return -1;
        }
    }
    void* mem = mmap(nullptr, (size_t)mapSize, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    close(fd);
    if (mem == MAP_FAILED) return -1;
    chmod(path, 0666);
    memset(mem, 0, UYVY_HEADER);
    uint32_t* hdr = (uint32_t*)mem;
    hdr[1] = (uint32_t)camera->width;
    hdr[2] = (uint32_t)camera->height;
    hdr[3] = (uint32_t)camera->stride;
    hdr[6] = (uint32_t)camera->size;
    __sync_synchronize();
    hdr[0] = UYVY_MAGIC;
    camera->shared = mem;
    camera->sharedBytes = mapSize;
    return 0;
}

static int grab(Camera* camera) {
    qcarcam_frame_info_t frame;
    memset(&frame, 0, sizeof(frame));
    int ret = p_get_frame(camera->handle, &frame, FRAME_TIMEOUT_NS, 0);
    if (ret != 0) return 0;
    if (frame.idx >= 5 || !camera->ptrs[frame.idx] || camera->ptrs[frame.idx] == MAP_FAILED || !camera->shared) {
        p_release_frame(camera->handle, frame.idx);
        return 0;
    }
    uint32_t* hdr = (uint32_t*)camera->shared;
    uint32_t slot = hdr[5] ^ 1u;
    uint8_t* dst = (uint8_t*)camera->shared + UYVY_HEADER + (size_t)slot * (size_t)camera->size;
    memcpy(dst, camera->ptrs[frame.idx], (size_t)camera->size);
    int released = p_release_frame(camera->handle, frame.idx);
    if (released != 0) fprintf(stderr, "release_frame %u -> %d\n", frame.idx, released);
    __sync_synchronize();
    hdr[5] = slot;
    __sync_synchronize();
    hdr[4] = hdr[4] + 1u;
    camera->seq += 1;
    return 1;
}

static int wanted(const int* ids, int count, int id) {
    for (int i = 0; i < count; i++) if (ids[i] == id) return 1;
    return 0;
}

static long long now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static int read_desired(int* ids) {
    FILE* file = fopen(DESIRED, "r");
    if (!file) return 0;
    int count = 0;
    char line[32];
    while (count < MAX_CAMERAS && fgets(line, sizeof(line), file)) {
        int id = atoi(line);
        if (id == 0 && line[0] != '0') continue;
        ids[count++] = id;
    }
    fclose(file);
    return count;
}

static int known_id(int id) {
    return id >= 0 && id < 10;
}

static void beat(void) {
    FILE* file = fopen(BEAT, "w");
    if (!file) return;
    fprintf(file, "%lld\n", (long long)(time(nullptr)));
    fclose(file);
    chmod(BEAT, 0666);
}

static int ram_mounted(void) {
    FILE* file = fopen("/proc/mounts", "r");
    if (!file) return 0;
    char line[512];
    int found = 0;
    while (fgets(line, sizeof(line), file)) {
        if (strstr(line, " " FRAME_RAM " ")) found = 1;
    }
    fclose(file);
    return found;
}

static void ensure_ram(void) {
    mkdir(FRAME_RAM, 0777);
    if (ram_mounted()) {
        chmod(FRAME_RAM, 0777);
        return;
    }
    if (mount("tmpfs", FRAME_RAM, "tmpfs", 0, "size=48m,mode=777") != 0) {
        fprintf(stderr, "tmpfs %s errno=%d\n", FRAME_RAM, errno);
        return;
    }
    chmod(FRAME_RAM, 0777);
    fprintf(stderr, "tmpfs %s\n", FRAME_RAM);
}

int main(void) {
    signal(SIGHUP, SIG_IGN);
    setvbuf(stderr, nullptr, _IONBF, 0);
    fprintf(stderr, "camd_grab start\n");
    ensure_ram();
    for (int id = 0; id < 10; id++) {
        char path[128];
        snprintf(path, sizeof(path), "/data/local/tmp/camd-uyvy-%d", id);
        unlink(path);
        snprintf(path, sizeof(path), "/data/local/tmp/camd-frame-%d.bin", id);
        unlink(path);
    }
    if (init_client() != 0) return 1;
    int ids[MAX_CAMERAS];
    int count = 0;
    long long last_beat_ms = -1000000;
    long long last_desired_ms = -1000000;
    while (1) {
        long long now = now_ms();
        if (now - last_beat_ms >= 1000) {
            beat();
            last_beat_ms = now;
        }
        if (now - last_desired_ms >= 250) {
            count = read_desired(ids);
            last_desired_ms = now;
        }
        for (int id = 0; id < 10; id++) {
            if (!open_cameras[id]) continue;
            if (wanted(ids, count, id)) continue;
            char path[128];
            snprintf(path, sizeof(path), FRAME_RAM "/camd-uyvy-%d", id);
            unlink(path);
            release_camera(open_cameras[id]);
            open_cameras[id] = nullptr;
        }
        for (int n = 0; n < count; n++) {
            int id = ids[n];
            if (!known_id(id) || open_cameras[id]) continue;
            if (next_try[id] > now) continue;
            Camera* opened = open_camera(id);
            fprintf(stderr, "open %d -> %s\n", id, opened ? "ok" : "fail");
            if (opened) open_cameras[id] = opened;
            else next_try[id] = now + 2000;
        }
        int got = 0;
        for (int id = 0; id < 10; id++) {
            if (open_cameras[id] && open_cameras[id]->handle && grab(open_cameras[id])) got = 1;
        }
        usleep(got ? 4000 : 20000);
    }
}

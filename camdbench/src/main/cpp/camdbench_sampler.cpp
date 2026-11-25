// Root CPU sampler for the camd A/B benchmark. Started by mb_daemon through run.sh.
//
// Usage: camdbench_sampler <raw> <ctl> <nonce> <max_sec> <interval_ms>
//
// Writes text records, one per line (see BenchAccumulator on the app side):
//   K <clk_tck>                  header
//   N <cpus>                     header
//   ZT <zone> <type>             header, thermal zone names
//   T <uptime_ms> <wall_ms>      sample start
//   C <total_ticks> <idle_ticks> aggregate /proc/stat (idle includes iowait)
//   F <cpu> <khz>                scaling_cur_freq
//   Z <zone> <millideg>          thermal zone temperature
//   M <kb>                       MemAvailable
//   P <pid> <ticks> <name>       process utime+stime, only when changed
//   H <pid> <tid> <ticks> <name> thread of our apps (vad.dashing.*, DCL ras.dashing.*,
//                                camd_grab, dclwm), only when changed
//   E                            end of run
// Stops when <ctl> no longer reads "run <nonce>" or after <max_sec>.

#include <dirent.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define MAX_ID 65536
#define MAX_ZONES 64
#define NAME_LEN 96

struct History {
    long long ticks[MAX_ID];
    long long start[MAX_ID];
};

// Main thread tid equals the pid, so threads keep a history of their own.
static History proc_history;
static History thread_history;
static char* proc_names[MAX_ID];
static long long proc_name_start[MAX_ID];

static long long now_ms(clockid_t clock) {
    struct timespec ts;
    clock_gettime(clock, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static int read_small(const char* path, char* buf, int size) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    int total = 0;
    while (total < size - 1) {
        int n = (int)read(fd, buf + total, (size_t)(size - 1 - total));
        if (n <= 0) break;
        total += n;
    }
    close(fd);
    buf[total] = 0;
    return total;
}

static void sanitize(char* s) {
    for (char* p = s; *p; p++) {
        if (*p == ' ' || *p == '\t' || *p == '\n' || *p == '\r') *p = '_';
    }
}

// Parses /proc/<pid>/stat or /proc/<pid>/task/<tid>/stat: comm, utime+stime, starttime.
static int parse_stat(const char* path, char* comm, int comm_size, long long* ticks, long long* start) {
    char buf[1024];
    if (read_small(path, buf, sizeof(buf)) <= 0) return 0;
    char* open_paren = strchr(buf, '(');
    char* close_paren = strrchr(buf, ')');
    if (!open_paren || !close_paren || close_paren < open_paren) return 0;
    int len = (int)(close_paren - open_paren - 1);
    if (len >= comm_size) len = comm_size - 1;
    memcpy(comm, open_paren + 1, (size_t)len);
    comm[len] = 0;
    sanitize(comm);
    // Fields after ')': state(0) ppid(1) ... utime(11) stime(12) ... starttime(19).
    char* p = close_paren + 2;
    long long utime = 0, stime = 0, starttime = 0;
    for (int field = 0; field <= 19 && *p; field++) {
        char* end = strchr(p, ' ');
        if (field == 11) utime = atoll(p);
        if (field == 12) stime = atoll(p);
        if (field == 19) starttime = atoll(p);
        if (!end) break;
        p = end + 1;
    }
    *ticks = utime + stime;
    *start = starttime;
    return 1;
}

static const char* process_name(int pid, const char* comm, long long start) {
    if (pid < MAX_ID && proc_names[pid] && proc_name_start[pid] == start) return proc_names[pid];
    char path[64];
    char cmd[256];
    snprintf(path, sizeof(path), "/proc/%d/cmdline", pid);
    int n = read_small(path, cmd, sizeof(cmd));
    const char* name = comm;
    if (n > 0 && cmd[0]) {
        const char* base = strrchr(cmd, '/');
        name = base ? base + 1 : cmd;
    }
    // The DCL window helper is a bare app_process; only its class name tells it apart.
    if (strncmp(name, "app_process", 11) == 0) {
        for (int i = (int)strlen(cmd) + 1; i < n; i += (int)strlen(cmd + i) + 1) {
            if (strncmp(cmd + i, "vad.dashing.dclwm", 17) == 0) {
                name = "dclwm";
                break;
            }
        }
    }
    char clean[NAME_LEN];
    snprintf(clean, sizeof(clean), "%.*s", NAME_LEN - 1, name[0] ? name : comm);
    sanitize(clean);
    if (pid >= MAX_ID) {
        static char scratch[NAME_LEN];
        snprintf(scratch, sizeof(scratch), "%s", clean);
        return scratch;
    }
    free(proc_names[pid]);
    proc_names[pid] = strdup(clean);
    proc_name_start[pid] = start;
    return proc_names[pid];
}

static int watched(const char* name) {
    if (strcmp(name, "camd_grab") == 0 || strcmp(name, "dclwm") == 0) return 1;
    return strncmp(name, "vad.dashing.", 12) == 0 || strncmp(name, "ras.dashing.", 12) == 0;
}

// Returns 1 when the id must be printed: first sample, new process/thread, or ticks changed.
static int changed(History* history, int id, long long ticks, long long start, int first) {
    if (id >= MAX_ID) return 1;
    int fresh = first || history->start[id] != start || history->ticks[id] != ticks;
    history->ticks[id] = ticks;
    history->start[id] = start;
    return fresh;
}

static void sample_threads(FILE* out, int pid, int first) {
    char dir_path[64];
    snprintf(dir_path, sizeof(dir_path), "/proc/%d/task", pid);
    DIR* dir = opendir(dir_path);
    if (!dir) return;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        int tid = atoi(entry->d_name);
        if (tid <= 0) continue;
        char path[96];
        char comm[NAME_LEN];
        long long ticks = 0, start = 0;
        snprintf(path, sizeof(path), "/proc/%d/task/%d/stat", pid, tid);
        if (!parse_stat(path, comm, sizeof(comm), &ticks, &start)) continue;
        if (changed(&thread_history, tid, ticks, start, first)) {
            fprintf(out, "H %d %d %lld %s\n", pid, tid, ticks, comm);
        }
    }
    closedir(dir);
}

static void sample_processes(FILE* out, int first) {
    DIR* dir = opendir("/proc");
    if (!dir) return;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        int pid = atoi(entry->d_name);
        if (pid <= 0) continue;
        char path[64];
        char comm[NAME_LEN];
        long long ticks = 0, start = 0;
        snprintf(path, sizeof(path), "/proc/%d/stat", pid);
        if (!parse_stat(path, comm, sizeof(comm), &ticks, &start)) continue;
        const char* name = process_name(pid, comm, start);
        if (changed(&proc_history, pid, ticks, start, first)) {
            fprintf(out, "P %d %lld %s\n", pid, ticks, name);
        }
        if (watched(name)) sample_threads(out, pid, first);
    }
    closedir(dir);
}

static void sample_cpu(FILE* out) {
    char buf[512];
    if (read_small("/proc/stat", buf, sizeof(buf)) <= 0) return;
    if (strncmp(buf, "cpu ", 4) != 0) return;
    long long v[10] = {0};
    char* p = buf + 4;
    for (int i = 0; i < 10; i++) {
        while (*p == ' ') p++;
        if (*p < '0' || *p > '9') break;
        v[i] = atoll(p);
        while (*p >= '0' && *p <= '9') p++;
    }
    // user nice system idle iowait irq softirq steal (guest time is already in user).
    long long total = v[0] + v[1] + v[2] + v[3] + v[4] + v[5] + v[6] + v[7];
    long long idle = v[3] + v[4];
    fprintf(out, "C %lld %lld\n", total, idle);
}

static void sample_freq(FILE* out, int cpus) {
    for (int cpu = 0; cpu < cpus; cpu++) {
        char path[96];
        char buf[32];
        snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", cpu);
        if (read_small(path, buf, sizeof(buf)) > 0) fprintf(out, "F %d %lld\n", cpu, atoll(buf));
    }
}

static int zone_count = 0;

static void header_zones(FILE* out) {
    for (int zone = 0; zone < MAX_ZONES; zone++) {
        char path[96];
        char type[64];
        snprintf(path, sizeof(path), "/sys/class/thermal/thermal_zone%d/type", zone);
        if (read_small(path, type, sizeof(type)) <= 0) break;
        sanitize(type);
        int len = (int)strlen(type);
        while (len > 0 && type[len - 1] == '_') type[--len] = 0;
        fprintf(out, "ZT %d %s\n", zone, type);
        zone_count = zone + 1;
    }
}

static void sample_zones(FILE* out) {
    for (int zone = 0; zone < zone_count; zone++) {
        char path[96];
        char buf[32];
        snprintf(path, sizeof(path), "/sys/class/thermal/thermal_zone%d/temp", zone);
        if (read_small(path, buf, sizeof(buf)) > 0) fprintf(out, "Z %d %lld\n", zone, atoll(buf));
    }
}

static void sample_mem(FILE* out) {
    char buf[2048];
    if (read_small("/proc/meminfo", buf, sizeof(buf)) <= 0) return;
    char* line = strstr(buf, "MemAvailable:");
    if (line) fprintf(out, "M %lld\n", atoll(line + 13));
}

static int keep_running(const char* ctl, const char* expected) {
    char buf[128];
    if (read_small(ctl, buf, sizeof(buf)) <= 0) return 0;
    int len = (int)strlen(buf);
    while (len > 0 && (buf[len - 1] == '\n' || buf[len - 1] == '\r' || buf[len - 1] == ' ')) buf[--len] = 0;
    return strcmp(buf, expected) == 0;
}

int main(int argc, char** argv) {
    if (argc < 6) {
        fprintf(stderr, "usage: camdbench_sampler <raw> <ctl> <nonce> <max_sec> <interval_ms>\n");
        return 2;
    }
    signal(SIGHUP, SIG_IGN);
    // The sampler must not compete with the code it measures.
    setpriority(PRIO_PROCESS, 0, 10);
    const char* raw = argv[1];
    const char* ctl = argv[2];
    char expected[128];
    snprintf(expected, sizeof(expected), "run %s", argv[3]);
    long long max_ms = atoll(argv[4]) * 1000;
    long long interval_ms = atoll(argv[5]);
    if (interval_ms < 500) interval_ms = 500;

    int fd = open(raw, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) {
        fprintf(stderr, "open %s failed\n", raw);
        return 1;
    }
    fchmod(fd, 0644);
    FILE* out = fdopen(fd, "w");
    int cpus = (int)sysconf(_SC_NPROCESSORS_CONF);
    fprintf(out, "K %ld\n", sysconf(_SC_CLK_TCK));
    fprintf(out, "N %d\n", cpus);
    header_zones(out);
    fflush(out);

    long long started = now_ms(CLOCK_MONOTONIC);
    int first = 1;
    while (1) {
        long long tick_start = now_ms(CLOCK_MONOTONIC);
        fprintf(out, "T %lld %lld\n", tick_start, now_ms(CLOCK_REALTIME));
        sample_cpu(out);
        sample_freq(out, cpus);
        sample_zones(out);
        sample_mem(out);
        sample_processes(out, first);
        fflush(out);
        first = 0;
        if (!keep_running(ctl, expected)) break;
        if (now_ms(CLOCK_MONOTONIC) - started >= max_ms) break;
        long long spent = now_ms(CLOCK_MONOTONIC) - tick_start;
        long long wait_ms = interval_ms - spent;
        if (wait_ms > 0) usleep((useconds_t)(wait_ms * 1000));
    }
    fprintf(out, "E\n");
    fclose(out);
    return 0;
}

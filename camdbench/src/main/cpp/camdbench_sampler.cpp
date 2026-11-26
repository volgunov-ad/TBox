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
//   CC <cpu> <total> <idle>      the same for one core
//   F <cpu> <khz>                scaling_cur_freq
//   Z <zone> <millideg>          thermal zone temperature
//   M <kb>                       MemAvailable
//   P <pid> <ticks> <name>       process utime+stime, only when changed
//   H <pid> <tid> <ticks> <name> thread of our apps (vad.dashing.*, DCL ras.dashing.*,
//                                camd_grab, dclwm), only when changed
//   A <pid> <cpus> <cpuset>      allowed cores and cpuset of our process, when changed
//   Q <tid> <cpu> <ticks>        ticks of our thread on that core since the last sample
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
#define MAX_CPUS 16
#define MAX_ACTIVE 1024
// Between full samples the threads that ran in the last interval are re-read this often,
// so their CPU time is put on the core they actually ran on.
#define CORE_POLL_MS 200

struct History {
    long long ticks[MAX_ID];
    long long start[MAX_ID];
};

// Main thread tid equals the pid, so threads keep a history of their own.
static History proc_history;
static History thread_history;
static char* proc_names[MAX_ID];
static long long proc_name_start[MAX_ID];
static char* proc_placement[MAX_ID];

// Core attribution of our threads: ticks seen last, ticks per core since the last full
// sample, and the threads to re-read between full samples.
static long long core_seen_ticks[MAX_ID];
static long long core_seen_start[MAX_ID];
static int core_ticks[MAX_ID][MAX_CPUS];
static int core_dirty[MAX_ID];
static int dirty_tids[MAX_ACTIVE];
static int dirty_count = 0;
struct Active {
    int pid;
    int tid;
};
static Active active[MAX_ACTIVE];
static int active_count = 0;
static Active next_active[MAX_ACTIVE];
static int next_active_count = 0;

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
static int parse_stat(const char* path, char* comm, int comm_size, long long* ticks, long long* start,
                      int* processor = nullptr) {
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
    // Fields after ')': state(0) ppid(1) ... utime(11) stime(12) ... starttime(19) ... processor(36).
    char* p = close_paren + 2;
    long long utime = 0, stime = 0, starttime = 0;
    int last_field = processor ? 36 : 19;
    for (int field = 0; field <= last_field && *p; field++) {
        char* end = strchr(p, ' ');
        if (field == 11) utime = atoll(p);
        if (field == 12) stime = atoll(p);
        if (field == 19) starttime = atoll(p);
        if (field == 36 && processor) *processor = atoi(p);
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

// Puts the ticks a thread used since it was last read on the core it last ran on.
// Returns 1 when the thread ran.
static int attribute_core(int tid, long long ticks, long long start, int cpu) {
    if (tid >= MAX_ID) return 0;
    if (core_seen_start[tid] != start) {
        core_seen_start[tid] = start;
        core_seen_ticks[tid] = ticks;
        return 0;
    }
    long long delta = ticks - core_seen_ticks[tid];
    core_seen_ticks[tid] = ticks;
    if (delta <= 0) return 0;
    if (cpu >= 0 && cpu < MAX_CPUS) {
        core_ticks[tid][cpu] += (int)delta;
        if (!core_dirty[tid] && dirty_count < MAX_ACTIVE) {
            core_dirty[tid] = 1;
            dirty_tids[dirty_count++] = tid;
        }
    }
    return 1;
}

static void poll_active_threads(void) {
    for (int i = 0; i < active_count; i++) {
        char path[96];
        char comm[NAME_LEN];
        long long ticks = 0, start = 0;
        int cpu = -1;
        snprintf(path, sizeof(path), "/proc/%d/task/%d/stat", active[i].pid, active[i].tid);
        if (!parse_stat(path, comm, sizeof(comm), &ticks, &start, &cpu)) continue;
        attribute_core(active[i].tid, ticks, start, cpu);
    }
}

static void flush_core_ticks(FILE* out) {
    for (int i = 0; i < dirty_count; i++) {
        int tid = dirty_tids[i];
        for (int cpu = 0; cpu < MAX_CPUS; cpu++) {
            if (core_ticks[tid][cpu] > 0) fprintf(out, "Q %d %d %d\n", tid, cpu, core_ticks[tid][cpu]);
            core_ticks[tid][cpu] = 0;
        }
        core_dirty[tid] = 0;
    }
    dirty_count = 0;
}

static void trim(char* s) {
    int len = (int)strlen(s);
    while (len > 0 && (s[len - 1] == '\n' || s[len - 1] == '\r' || s[len - 1] == ' ')) s[--len] = 0;
}

static void sample_placement(FILE* out, int pid) {
    char path[64];
    char status[4096];
    char cpus[64] = "?";
    char cpuset[96] = "?";
    snprintf(path, sizeof(path), "/proc/%d/status", pid);
    if (read_small(path, status, sizeof(status)) > 0) {
        char* line = strstr(status, "Cpus_allowed_list:");
        if (line) {
            line += 18;
            while (*line == ' ' || *line == '\t') line++;
            snprintf(cpus, sizeof(cpus), "%.*s", (int)strcspn(line, "\n"), line);
        }
    }
    snprintf(path, sizeof(path), "/proc/%d/cpuset", pid);
    if (read_small(path, cpuset, sizeof(cpuset)) > 0) trim(cpuset);
    if (!cpuset[0]) snprintf(cpuset, sizeof(cpuset), "/");
    sanitize(cpus);
    sanitize(cpuset);
    char line[192];
    snprintf(line, sizeof(line), "%s %s", cpus, cpuset);
    if (pid < MAX_ID && proc_placement[pid] && strcmp(proc_placement[pid], line) == 0) return;
    fprintf(out, "A %d %s\n", pid, line);
    if (pid < MAX_ID) {
        free(proc_placement[pid]);
        proc_placement[pid] = strdup(line);
    }
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
        int cpu = -1;
        snprintf(path, sizeof(path), "/proc/%d/task/%d/stat", pid, tid);
        if (!parse_stat(path, comm, sizeof(comm), &ticks, &start, &cpu)) continue;
        if (attribute_core(tid, ticks, start, cpu) && next_active_count < MAX_ACTIVE) {
            next_active[next_active_count].pid = pid;
            next_active[next_active_count].tid = tid;
            next_active_count++;
        }
        if (changed(&thread_history, tid, ticks, start, first)) {
            fprintf(out, "H %d %d %lld %s\n", pid, tid, ticks, comm);
        }
    }
    closedir(dir);
}

static long long placement_start[MAX_ID];

// A reused pid must print its placement again.
static int changed_start_placement(int pid, long long start) {
    if (placement_start[pid] == start) return 0;
    placement_start[pid] = start;
    return 1;
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
        if (watched(name)) {
            if (pid < MAX_ID && changed_start_placement(pid, start)) {
                free(proc_placement[pid]);
                proc_placement[pid] = nullptr;
            }
            sample_placement(out, pid);
            sample_threads(out, pid, first);
        }
    }
    closedir(dir);
}

// Parses one "cpu..." line of /proc/stat into total and idle ticks.
static void cpu_line(const char* p, long long* total, long long* idle) {
    long long v[10] = {0};
    for (int i = 0; i < 10; i++) {
        while (*p == ' ') p++;
        if (*p < '0' || *p > '9') break;
        v[i] = atoll(p);
        while (*p >= '0' && *p <= '9') p++;
    }
    // user nice system idle iowait irq softirq steal (guest time is already in user).
    *total = v[0] + v[1] + v[2] + v[3] + v[4] + v[5] + v[6] + v[7];
    *idle = v[3] + v[4];
}

static void sample_cpu(FILE* out) {
    static char buf[8192];
    if (read_small("/proc/stat", buf, sizeof(buf)) <= 0) return;
    if (strncmp(buf, "cpu ", 4) != 0) return;
    long long total = 0, idle = 0;
    cpu_line(buf + 4, &total, &idle);
    fprintf(out, "C %lld %lld\n", total, idle);
    for (char* line = strchr(buf, '\n'); line; line = strchr(line, '\n')) {
        line++;
        if (strncmp(line, "cpu", 3) != 0 || line[3] < '0' || line[3] > '9') break;
        int cpu = atoi(line + 3);
        const char* values = strchr(line, ' ');
        if (!values) break;
        cpu_line(values, &total, &idle);
        fprintf(out, "CC %d %lld %lld\n", cpu, total, idle);
    }
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
        next_active_count = 0;
        sample_processes(out, first);
        flush_core_ticks(out);
        memcpy(active, next_active, sizeof(Active) * (size_t)next_active_count);
        active_count = next_active_count;
        fflush(out);
        first = 0;
        if (!keep_running(ctl, expected)) break;
        if (now_ms(CLOCK_MONOTONIC) - started >= max_ms) break;
        while (1) {
            long long wait_ms = interval_ms - (now_ms(CLOCK_MONOTONIC) - tick_start);
            if (wait_ms <= 0) break;
            usleep((useconds_t)((wait_ms < CORE_POLL_MS ? wait_ms : CORE_POLL_MS) * 1000));
            if (now_ms(CLOCK_MONOTONIC) - tick_start < interval_ms) poll_active_threads();
        }
    }
    fprintf(out, "E\n");
    fclose(out);
    return 0;
}

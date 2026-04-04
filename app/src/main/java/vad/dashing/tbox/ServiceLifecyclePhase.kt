package vad.dashing.tbox

/**
 * High-level state for [BackgroundService] startup / shutdown. Used for logging and tests;
 * [BackgroundService.servicePhase] is the source of truth.
 */
enum class ServiceLifecyclePhase {
    Idle,
    Starting,
    Running,
    Stopping,
}

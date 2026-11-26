package vad.dashing.rootsvc.client;

/**
 * Client-side outcome of one rootsvc call. {@link Kind#UNAVAILABLE} never appears on the wire;
 * the client invents it when the socket is missing or ACK never arrives.
 */
public final class RootSvcResult {
    public enum Kind {
        OK,
        FAILED,
        REJECTED,
        UNKNOWN_OUTCOME,
        UNAVAILABLE
    }

    public final Kind kind;
    public final String id;
    public final String err;
    public final String msg;
    public final Integer exit;
    public final String stdout;
    public final String stderr;
    public final Long ms;
    public final String reason;
    public final String raw;

    private RootSvcResult(
            Kind kind,
            String id,
            String err,
            String msg,
            Integer exit,
            String stdout,
            String stderr,
            Long ms,
            String reason,
            String raw) {
        this.kind = kind;
        this.id = id;
        this.err = err;
        this.msg = msg;
        this.exit = exit;
        this.stdout = stdout;
        this.stderr = stderr;
        this.ms = ms;
        this.reason = reason;
        this.raw = raw;
    }

    public boolean isOk() {
        return kind == Kind.OK;
    }

    public boolean isErr(String code) {
        return err != null && err.equals(code);
    }

    public static RootSvcResult ok(
            String id, Integer exit, String stdout, String stderr, Long ms, String raw) {
        return new RootSvcResult(Kind.OK, id, null, null, exit, stdout, stderr, ms, null, raw);
    }

    public static RootSvcResult failed(
            String id, Integer exit, String stdout, String stderr, Long ms, String msg, String raw) {
        return new RootSvcResult(
                Kind.FAILED, id, RootSvcProtocol.ERR_FAILED, msg, exit, stdout, stderr, ms, null, raw);
    }

    public static RootSvcResult rejected(String id, String err, String msg, String raw) {
        return new RootSvcResult(Kind.REJECTED, id, err, msg, null, null, null, null, null, raw);
    }

    public static RootSvcResult unknownOutcome(String id, String reason) {
        return new RootSvcResult(
                Kind.UNKNOWN_OUTCOME, id, null, null, null, null, null, null, reason, null);
    }

    public static RootSvcResult unavailable(String reason) {
        return new RootSvcResult(
                Kind.UNAVAILABLE, null, null, null, null, null, null, null, reason, null);
    }
}

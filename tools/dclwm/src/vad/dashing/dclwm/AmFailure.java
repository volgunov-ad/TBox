package vad.dashing.dclwm;

/** Non-zero exit or a timeout from `am` / `pm`. */
public final class AmFailure extends Exception {
    public final String output;

    public AmFailure(String output) {
        super(oneLine(output));
        this.output = output == null ? "" : output;
    }

    static String oneLine(String output) {
        if (output == null || output.trim().isEmpty()) {
            return "am_failed";
        }
        String[] lines = output.trim().split("\n");
        String line = lines[0].trim();
        if (line.length() > 160) {
            return line.substring(0, 160);
        }
        return line;
    }
}

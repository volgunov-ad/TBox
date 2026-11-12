package vad.dashing.dclwm;

/** One task line from `am stack list`. */
public final class WindowTask {
    public final int id;
    public final int stackId;
    public final String packageName;
    public final String activity;
    public final String mode;
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;
    public final int stackLeft;
    public final int stackTop;
    public final int stackRight;
    public final int stackBottom;
    public final boolean visible;

    public WindowTask(
            int id,
            int stackId,
            String packageName,
            String activity,
            String mode,
            int left,
            int top,
            int right,
            int bottom,
            int stackLeft,
            int stackTop,
            int stackRight,
            int stackBottom,
            boolean visible) {
        this.id = id;
        this.stackId = stackId;
        this.packageName = packageName;
        this.activity = activity;
        this.mode = mode;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.stackLeft = stackLeft;
        this.stackTop = stackTop;
        this.stackRight = stackRight;
        this.stackBottom = stackBottom;
        this.visible = visible;
    }

    public String format() {
        return "task id=" + id
                + " stack=" + stackId
                + " pkg=" + packageName
                + " activity=" + activity
                + " mode=" + mode
                + " bounds=" + left + "," + top + "," + right + "," + bottom
                + " visible=" + (visible ? 1 : 0);
    }
}

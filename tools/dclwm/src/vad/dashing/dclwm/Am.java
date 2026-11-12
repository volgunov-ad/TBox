package vad.dashing.dclwm;

/** Runs one `am` or `pm` command. Arguments are already split; nothing goes through a shell. */
public interface Am {
    String run(String... args) throws AmFailure;
}

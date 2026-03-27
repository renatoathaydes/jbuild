package jb;

final class JbProcess {
    static ProcessBuilder runJb(String command) {
        var shell = System.getenv("SHELL");
        if (shell == null) {
            shell = "bash";
        }
        return new ProcessBuilder(shell, "-c", command);
    }
}

package jb;

import java.io.File;

final class JbProcess {
    static ProcessBuilder runJb(String command) {
        var shell = System.getenv("SHELL");
        if (shell == null) {
            shell = "bash";
        }
        var jbHome = System.getenv("JB_HOME");
        String jb;
        if (jbHome == null) {
            jb = "jb";
        } else {
            // resolve to an absolute path as jb may run with a different working
            // directory than the one JB_HOME was set relative to.
            var jbBin = new File(new File(jbHome), "bin");
            var jbExe = System.getProperty("os.name").toLowerCase().contains("win")
                    ? "jb.exe"
                    : "jb";
            jb = new File(jbBin, jbExe).getAbsolutePath();
        }

        return new ProcessBuilder(shell, "-c", jb + ' ' + command);
    }
}

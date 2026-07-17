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
            if (!jbHome.isEmpty() && !jbHome.endsWith(File.separator)) {
                jbHome += File.separator;
            }
            jb = jbHome + "bin" + File.separator + "jb";
        }

        return new ProcessBuilder(shell, "-c", jb + ' ' + command);
    }
}

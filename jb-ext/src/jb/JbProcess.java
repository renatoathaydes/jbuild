package jb;

import java.nio.file.Paths;

final class JbProcess {
    static ProcessBuilder runJb(String... args) {
        var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        var jbHome = System.getenv("JB_HOME");
        var jbExe = isWindows ? "jb.exe" : "jb";
        String jb;
        if (jbHome == null) {
            jb = jbExe;
        } else {
            jb = Paths.get(jbHome, "bin", jbExe).toAbsolutePath().toString();
        }

        var cmd = new String[args.length + 1];
        cmd[0] = jb;
        System.arraycopy(args, 0, cmd, 1, args.length);
        return new ProcessBuilder(cmd);
    }
}

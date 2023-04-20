package jbuild.java.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CreateJarOptions {

    public final String name;
    public final String mainClass;
    public final boolean noManifest;
    public final String moduleVersion;
    public final FileSet fileSet;
    public final Map<String, FileSet> filesPerRelease;

    public CreateJarOptions(String name,
                            String mainClass,
                            boolean noManifest,
                            String moduleVersion,
                            FileSet fileSet,
                            Map<String, FileSet> filesPerRelease) {
        this.name = name;
        this.mainClass = mainClass;
        this.noManifest = noManifest;
        this.moduleVersion = moduleVersion;
        this.fileSet = fileSet;
        this.filesPerRelease = filesPerRelease;
    }

    public List<String> toArgs(boolean createOutputDir) {
        var result = new ArrayList<String>();
        result.add("--create");
        result.add("--file");
        if (name.isBlank()) {
            result.add("lib.jar");
        } else {
            if (createOutputDir) createDirForFile(name);
            result.add(name);
        }
        if (!mainClass.isBlank()) {
            result.add("--main-class");
            result.add(mainClass);
        }
        if (noManifest) {
            result.add("--no-manifest");
        }
        if (!moduleVersion.isBlank()) {
            result.add("--module-version");
            result.add(moduleVersion);
        }
        addFileSetTo(result, fileSet);
        filesPerRelease.forEach((release, fileSet) -> {
            result.add("--release");
            result.add(release);
            addFileSetTo(result, fileSet);
        });
        return result;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void createDirForFile(String name) {
        var file = new File(name);
        var parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs(); // best-effort attempt, don't fail yet
        }
    }

    static void addFileSetTo(List<String> result, FileSet fileSet) {
        if (!fileSet.rootDir.isBlank()) {
            result.add("-C");
            result.add(fileSet.rootDir);
        }
        if (fileSet.isEmpty()) {
            result.add(".");
        } else {
            result.addAll(fileSet.files);
        }
    }

    public static final class FileSet {

        public final Set<String> files;
        public final String rootDir;

        public FileSet(Set<String> files, String rootDir) {
            this.files = files;
            this.rootDir = rootDir;
        }

        public boolean isEmpty() {
            return files.isEmpty();
        }
    }
}

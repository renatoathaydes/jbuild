package jbuild.commands;

import jbuild.classes.ClassFileException;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.MajorVersion;
import jbuild.classes.model.attributes.ModuleAttribute;
import jbuild.classes.parser.JBuildClassFileParser;
import jbuild.java.JavaVersionHelper;
import jbuild.log.JBuildLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ShowModuleCommand {

    /**
     * Sum type: permits {@link JavaModule}, {@link AutomaticModule}, {@link SimpleJar}.
     */
    public interface ModuleOrJar {
        File getFile();

        String getJavaVersion();
    }

    public static final class JavaModule implements ModuleOrJar {
        private final File file;
        private final ModuleAttribute moduleAttribute;
        private final String javaVersion;

        public JavaModule(File file, ModuleAttribute moduleAttribute, String javaVersion) {
            this.file = file;
            this.moduleAttribute = moduleAttribute;
            this.javaVersion = javaVersion;
        }

        public ModuleAttribute getModuleAttribute() {
            return moduleAttribute;
        }

        public String getModuleName() {
            return moduleAttribute.moduleName;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public String getJavaVersion() {
            return javaVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            JavaModule that = (JavaModule) o;
            return file.equals(that.file) && moduleAttribute.equals(that.moduleAttribute) && javaVersion.equals(that.javaVersion);
        }

        @Override
        public int hashCode() {
            int result = file.hashCode();
            result = 31 * result + moduleAttribute.hashCode();
            result = 31 * result + javaVersion.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "JavaModule{" +
                    "file=" + file +
                    ", moduleAttribute=" + moduleAttribute +
                    ", javaVersion='" + javaVersion + '\'' +
                    '}';
        }
    }

    public static final class AutomaticModule implements ModuleOrJar {
        private final File file;
        private final String moduleName;
        private final String javaVersion;

        public AutomaticModule(File file, String moduleName, String javaVersion) {
            this.file = file;
            this.moduleName = moduleName;
            this.javaVersion = javaVersion;
        }

        public String getModuleName() {
            return moduleName;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public String getJavaVersion() {
            return javaVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            AutomaticModule that = (AutomaticModule) o;
            return file.equals(that.file) && moduleName.equals(that.moduleName) && javaVersion.equals(that.javaVersion);
        }

        @Override
        public int hashCode() {
            int result = file.hashCode();
            result = 31 * result + moduleName.hashCode();
            result = 31 * result + javaVersion.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "AutomaticModule{" +
                    "file=" + file +
                    ", moduleName='" + moduleName + '\'' +
                    ", javaVersion='" + javaVersion + '\'' +
                    '}';
        }
    }

    public static final class SimpleJar implements ModuleOrJar {
        private final File file;
        private final String javaVersion;

        public SimpleJar(File file, String javaVersion) {
            this.file = file;
            this.javaVersion = javaVersion;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public String getJavaVersion() {
            return javaVersion;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            SimpleJar simpleJar = (SimpleJar) o;
            return file.equals(simpleJar.file) && javaVersion.equals(simpleJar.javaVersion);
        }

        @Override
        public int hashCode() {
            int result = file.hashCode();
            result = 31 * result + javaVersion.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "SimpleJar{" +
                    "file=" + file +
                    ", javaVersion='" + javaVersion + '\'' +
                    '}';
        }
    }

    private final JBuildLog log;

    public ShowModuleCommand(JBuildLog log) {
        this.log = log;
    }

    /**
     * Show whether the jars or class files are modules, and if so, details about them.
     *
     * @param jarsOrClassFiles jar files or class files
     * @return true if ok, false otherwise
     */
    public boolean show(List<String> jarsOrClassFiles) {
        var result = new AtomicBoolean(true);
        check(jarsOrClassFiles, (moduleOrJar) -> {
            if (moduleOrJar instanceof SimpleJar) {
                log.println("File " + moduleOrJar.getFile() + " is not a module.");
                log.println("  JavaVersion: " + moduleOrJar.getJavaVersion());
            } else if (moduleOrJar instanceof AutomaticModule) {
                log.println("File " + moduleOrJar.getFile() + " is an automatic-module: " +
                        ((AutomaticModule) moduleOrJar).getModuleName());
                log.println("  JavaVersion: " + moduleOrJar.getJavaVersion());
            } else if (moduleOrJar instanceof JavaModule) {
                showModule((JavaModule) moduleOrJar);
            }
        }, (file, error) -> {
            log.println("ERROR: " + file.getPath() + ": " + error);
            result.set(false);
        });
        return result.get();
    }

    /**
     * Check whether the jars or class files are modules, invoking the given callbacks for each file.
     *
     * @param jarsOrClassFiles jar files or class files
     * @param onModuleInfo     callback for module or jar files
     * @param onError          callback for errors. The first argument is the file path, the second is an error message.
     */
    public void check(List<String> jarsOrClassFiles,
                      Consumer<ModuleOrJar> onModuleInfo,
                      BiConsumer<File, String> onError) {
        for (String jarsOrClassFile : jarsOrClassFiles) {
            var file = new File(jarsOrClassFile);
            if (file.isFile()) {
                check(file, onModuleInfo, onError);
            } else {
                onError.accept(file, "not a file");
            }
        }
    }

    private void check(File file,
                       Consumer<ModuleOrJar> onModuleInfo,
                       BiConsumer<File, String> onError) {
        try {
            if (file.getName().endsWith(".jar")) {
                log.verbosePrintln(() -> "Showing a jar file: " + file);
                checkJar(file, onModuleInfo, onError);
            } else {
                log.verbosePrintln(() -> "Trying to parse as class file: " + file);
                checkClassFile(file, Files.newInputStream(file.toPath(), StandardOpenOption.READ),
                        onModuleInfo, onError);
            }
        } catch (IOException e) {
            onError.accept(file, e.getMessage());
        } catch (Exception e) {
            onError.accept(file, e.toString());
        }
    }

    private void checkJar(File file,
                          Consumer<ModuleOrJar> onModuleInfo,
                          BiConsumer<File, String> onError) throws IOException {
        String javaVersion;
        try (var jar = new JarFile(file, false, ZipFile.OPEN_READ)) {
            var moduleEntry = findModuleInfo(jar);
            if (moduleEntry == null) {
                log.verbosePrintln(() -> "No module-info found in " + file + ", looking for Automatic-Module");
                var classFile = findAnyClassFile(jar, onError);
                javaVersion = getJavaVersion(classFile);
                var automaticModule = findAutomaticModule(jar);
                if (automaticModule == null) {
                    onModuleInfo.accept(new SimpleJar(file, javaVersion));
                } else {
                    onModuleInfo.accept(new AutomaticModule(file, automaticModule, javaVersion));
                    log.verbosePrintln(() -> "Automatic-Module found in " + file + ": " + automaticModule);
                }
                return;
            }
            log.verbosePrintln(() -> "Found module-info.class in " + file);
            try (var stream = jar.getInputStream(moduleEntry)) {
                checkClassFile(file, stream, onModuleInfo, onError);
            }
        }
    }

    private String findAutomaticModule(JarFile jar) throws IOException {
        var manifest = jar.getManifest();
        if (manifest == null) return null;
        return manifest.getMainAttributes().getValue("Automatic-Module-Name");
    }

    private ClassFile findAnyClassFile(JarFile jar,
                                       BiConsumer<File, String> onError) throws IOException {
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.getName().endsWith(".class")) {
                log.verbosePrintln(() -> "Found a class file in " + jar.getName() + ": " + entry.getName());
                try (var stream = jar.getInputStream(entry)) {
                    return parseClassFile(entry.getName(), stream, onError);
                }
            }
        }
        return null;
    }

    private ZipEntry findModuleInfo(JarFile jar) throws IOException {
        var entry = jar.getEntry("module-info.class");
        if (entry != null) return entry;
        log.verbosePrintln(() -> "Checking if " + jar.getName() + " is Multi-Release");
        var manifest = jar.getManifest();
        if (manifest != null) {
            var multiRelease = jar.getManifest().getMainAttributes().getValue("Multi-Release");
            if (!Boolean.parseBoolean(multiRelease)
                    || jar.getEntry("META-INF/versions/") == null) {
                log.verbosePrintln(() -> jar.getName() + " is not Multi-Release");
                return null;
            }
        }
        var javaVersion = JavaVersionHelper.currentJavaVersion();
        while (javaVersion > 7) {
            entry = jar.getEntry("META-INF/versions/" + javaVersion + "/module-info.class");
            if (entry != null) {
                final int version = javaVersion;
                log.verbosePrintln(() -> "File " + jar.getName() + " has a module-info file at release=" + version);
                return entry;
            }
            javaVersion--;
        }
        return null;
    }

    private ClassFile parseClassFile(String path, InputStream stream,
                                     BiConsumer<File, String> onError) throws IOException {
        log.verbosePrintln(() -> "Parsing class file in " + path);
        try {
            return new JBuildClassFileParser().parse(stream);
        } catch (ClassFileException e) {
            onError.accept(new File(path), "invalid class file: " + e.getMessage());
            return null;
        }
    }

    private void checkClassFile(File file,
                                InputStream stream,
                                Consumer<ModuleOrJar> onModuleInfo,
                                BiConsumer<File, String> onError) throws IOException {
        var classFile = parseClassFile(file.getPath(), stream, onError);
        if (classFile != null) {
            check(file.getPath(), classFile, onModuleInfo, onError);
        }
    }

    private void check(String path, ClassFile classFile,
                       Consumer<ModuleOrJar> onModuleInfo,
                       BiConsumer<File, String> onError) {
        var moduleAttribute = classFile.getModuleAttribute().orElse(null);
        if (moduleAttribute == null) {
            onError.accept(new File(path), "invalid module-info.class file: missing Module Attribute");
            return;
        }
        onModuleInfo.accept(new JavaModule(new File(path), moduleAttribute, getJavaVersion(classFile)));
    }

    private static String getJavaVersion(ClassFile classFile) {
        if (classFile == null) return "unknown";
        return classFile.majorVersion.toKnownVersion()
                .map(MajorVersion.Known::displayName)
                .orElse("unknown");
    }

    private void showModule(JavaModule javaModule) {
        var moduleAttribute = javaModule.getModuleAttribute();
        log.println("File " + javaModule.getFile() + " contains a Java module:");
        log.println("  JavaVersion: " + javaModule.getJavaVersion());
        log.println("  Name: " + moduleAttribute.moduleName);
        log.println("  Version: " + moduleAttribute.moduleVersion);
        log.println("  Flags: " + flagsOf(moduleAttribute));
        showRequires(moduleAttribute.requires);
        showExports(moduleAttribute.exports);
        showOpens(moduleAttribute.opens);
        showUses(moduleAttribute.uses);
        showProvides(moduleAttribute.provides);
    }

    private void showRequires(List<ModuleAttribute.Requires> requires) {
        log.println("  Requires:");
        if (requires.isEmpty()) {
            return;
        }
        for (ModuleAttribute.Requires require : requires) {
            log.println("    Module: " + require.moduleName);
            log.println("      Version: " + require.version);
            log.println("      Flags: " + flagsOf(require));
        }
    }

    private void showExports(List<ModuleAttribute.Exports> exports) {
        log.println("  Exports:");
        if (exports.isEmpty()) {
            return;
        }
        for (ModuleAttribute.Exports export : exports) {
            log.println("    Package: " + export.packageName);
            log.println("      Flags: " + flagsOf(export));
            log.println("      ToModules: " + String.join(" ", export.exportsToModules));
        }
    }

    private void showOpens(List<ModuleAttribute.Opens> opens) {
        log.println("  Opens:");
        if (opens.isEmpty()) {
            return;
        }
        for (ModuleAttribute.Opens open : opens) {
            log.println("    Package: " + open.packageName);
            log.println("      Flags: " + flagsOf(open));
            log.println("      ToModules: " + String.join(" ", open.opensToModules));
        }
    }

    private void showUses(Set<String> uses) {
        log.println("  Uses: " + String.join(" ", uses));
    }

    private void showProvides(List<ModuleAttribute.Provides> provides) {
        log.println("  Provides:");
        if (provides.isEmpty()) {
            return;
        }
        for (ModuleAttribute.Provides provide : provides) {
            log.println("    Service: " + provide.serviceName);
            log.println("      With: " + String.join(" ", provide.providesWith));
        }
    }

    private static String flagsOf(ModuleAttribute moduleAttribute) {
        if (moduleAttribute.moduleFlags == 0) {
            return "none";
        }
        var flags = new ArrayList<String>(3);
        if (moduleAttribute.isOpen()) {
            flags.add("open");
        }
        if (moduleAttribute.isSynthetic()) {
            flags.add("synthetic");
        }
        if (moduleAttribute.isMandated()) {
            flags.add("mandated");
        }
        return String.join(" ", flags);
    }

    private static String flagsOf(ModuleAttribute.Exports exports) {
        if (exports.exportsFlags == 0) {
            return "none";
        }
        var flags = new ArrayList<String>(2);
        if (exports.isSynthetic()) {
            flags.add("synthetic");
        }
        if (exports.isMandated()) {
            flags.add("mandated");
        }
        return String.join(" ", flags);
    }

    private static String flagsOf(ModuleAttribute.Opens open) {
        if (open.opensFlags == 0) {
            return "none";
        }
        var flags = new ArrayList<String>(2);
        if (open.isSynthetic()) {
            flags.add("synthetic");
        }
        if (open.isMandated()) {
            flags.add("mandated");
        }
        return String.join(" ", flags);
    }

    private static String flagsOf(ModuleAttribute.Requires requires) {
        if (requires.requiresFlags == 0) {
            return "none";
        }
        var flags = new ArrayList<String>(4);
        if (requires.isTransitive()) {
            flags.add("transitive");
        }
        if (requires.isStaticPhase()) {
            flags.add("static");
        }
        if (requires.isSynthetic()) {
            flags.add("synthetic");
        }
        if (requires.isMandated()) {
            flags.add("mandated");
        }
        return String.join(" ", flags);
    }
}

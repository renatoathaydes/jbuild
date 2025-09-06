package jbuild.commands;

import jbuild.classes.ClassFileException;
import jbuild.classes.model.ClassFile;
import jbuild.classes.model.attributes.ModuleAttribute;
import jbuild.classes.parser.JBuildClassFileParser;
import jbuild.log.JBuildLog;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

public final class ShowModuleCommand {

    private final JBuildLog log;

    public ShowModuleCommand(JBuildLog log) {
        this.log = log;
    }

    public void show(Collection<String> jarsOrClassFiles) {
        for (String jarsOrClassFile : jarsOrClassFiles) {
            var file = new File(jarsOrClassFile);
            if (file.isFile()) {
                show(file);
            } else {
                log.println("ERROR: not a file: " + jarsOrClassFile);
            }
        }
    }

    private void show(File file) {
        try {
            if (file.getName().endsWith(".jar")) {
                log.verbosePrintln(() -> "Showing a jar file: " + file);
                showJar(file);
            } else {
                log.verbosePrintln(() -> "Trying to parse as class file: " + file);
                showClassFile(file, Files.newInputStream(file.toPath(), StandardOpenOption.READ));
            }
        } catch (IOException e) {
            log.println("ERROR: Reading file : " + file + ": " + e.getMessage());
        } catch (Exception e) {
            log.println("ERROR: Reading file : " + file + ": " + e);
        }
    }

    private void showJar(File file) throws IOException {
        try (ZipFile zipFile = new ZipFile(file, ZipFile.OPEN_READ)) {
            var moduleEntry = zipFile.getEntry("module-info.class");
            if (moduleEntry == null) {
                log.println("Jar " + file + " is not a module.");
                return;
            }
            try (var stream = zipFile.getInputStream(moduleEntry)) {
                showClassFile(file, stream);
            }
        }
    }

    private void showClassFile(File file, InputStream stream) throws IOException {
        ClassFile classFile;
        try {
            classFile = new JBuildClassFileParser().parse(stream);
        } catch (ClassFileException e) {
            log.println("ERROR: File : " + file + " is not a valid class file: " + e.getMessage());
            return;
        }
        var moduleAttribute = classFile.getModuleAttribute().orElse(null);
        if (moduleAttribute == null) {
            log.println("File " + file + " is not a module.");
            return;
        }
        show(file.getPath(), moduleAttribute);
    }

    private void show(String path, ModuleAttribute moduleAttribute) {
        log.println("File " + path + " contains a Java module:");
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

package jbuild.api.config;

import java.util.List;
import java.util.Map;

/**
 * The jb configuration.
 * <p>
 * Does not include custom tasks' configurations.
 * <p>
 * Common configuration (visible by multiple tasks) can be provided via jb properties.
 */
public final class JbConfig {
    public final String group;
    public final String module;
    public final String name;
    public final String version;
    public final String description;
    public final String url;
    public final String mainClass;
    public final String extensionProject;
    public final List<String> sourceDirs;
    public final String outputDir;
    public final String outputJar;
    public final List<String> resourceDirs;
    public final List<String> repositories;
    public final Map<String, DependencySpec> dependencies;
    public final Map<String, DependencySpec> processorDependencies;
    public final List<String> dependencyExclusionPatterns;
    public final List<String> processorDependencyExclusionPatterns;
    public final String compileLibsDir;
    public final String runtimeLibsDir;
    public final String testReportsDir;
    public final List<String> javacArgs;
    public final List<String> runJavaArgs;
    public final List<String> testJavaArgs;
    public final Map<String, String> javacEnv;
    public final Map<String, String> runJavaEnv;
    public final Map<String, String> testJavaEnv;
    public final SourceControlManagement scm;
    public final List<Developer> developers;
    public final List<String> licenses;
    public final Map<String, Object> properties;

    public JbConfig(String group,
                    String module,
                    String name,
                    String version,
                    String description,
                    String url,
                    String mainClass,
                    String extensionProject,
                    List<String> sourceDirs,
                    String outputDir,
                    String outputJar,
                    List<String> resourceDirs,
                    List<String> repositories,
                    Map<String, DependencySpec> dependencies,
                    Map<String, DependencySpec> processorDependencies,
                    List<String> dependencyExclusionPatterns,
                    List<String> processorDependencyExclusionPatterns,
                    String compileLibsDir,
                    String runtimeLibsDir,
                    String testReportsDir,
                    List<String> javacArgs,
                    List<String> runJavaArgs,
                    List<String> testJavaArgs,
                    Map<String, String> javacEnv,
                    Map<String, String> runJavaEnv,
                    Map<String, String> testJavaEnv,
                    SourceControlManagement scm,
                    List<Developer> developers,
                    List<String> licenses,
                    Map<String, Object> properties) {
        this.group = group;
        this.module = module;
        this.name = name;
        this.version = version;
        this.description = description;
        this.url = url;
        this.mainClass = mainClass;
        this.extensionProject = extensionProject;
        this.sourceDirs = sourceDirs;
        this.outputDir = outputDir;
        this.outputJar = outputJar;
        this.resourceDirs = resourceDirs;
        this.repositories = repositories;
        this.dependencies = dependencies;
        this.processorDependencies = processorDependencies;
        this.dependencyExclusionPatterns = dependencyExclusionPatterns;
        this.processorDependencyExclusionPatterns = processorDependencyExclusionPatterns;
        this.compileLibsDir = compileLibsDir;
        this.runtimeLibsDir = runtimeLibsDir;
        this.testReportsDir = testReportsDir;
        this.javacArgs = javacArgs;
        this.runJavaArgs = runJavaArgs;
        this.testJavaArgs = testJavaArgs;
        this.javacEnv = javacEnv;
        this.runJavaEnv = runJavaEnv;
        this.testJavaEnv = testJavaEnv;
        this.scm = scm;
        this.developers = developers;
        this.licenses = licenses;
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "JbConfig{" +
                "group='" + group + '\'' +
                ", module='" + module + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", description='" + description + '\'' +
                ", url='" + url + '\'' +
                ", mainClass='" + mainClass + '\'' +
                ", extensionProject='" + extensionProject + '\'' +
                ", sourceDirs=" + sourceDirs +
                ", outputDir='" + outputDir + '\'' +
                ", outputJar='" + outputJar + '\'' +
                ", resourceDirs=" + resourceDirs +
                ", repositories=" + repositories +
                ", dependencies=" + dependencies +
                ", processorDependencies=" + processorDependencies +
                ", dependencyExclusionPatterns=" + dependencyExclusionPatterns +
                ", processorDependencyExclusionPatterns=" + processorDependencyExclusionPatterns +
                ", compileLibsDir='" + compileLibsDir + '\'' +
                ", runtimeLibsDir='" + runtimeLibsDir + '\'' +
                ", testReportsDir='" + testReportsDir + '\'' +
                ", javacArgs=" + javacArgs +
                ", runJavaArgs=" + runJavaArgs +
                ", testJavaArgs=" + testJavaArgs +
                ", javacEnv=" + javacEnv +
                ", runJavaEnv=" + runJavaEnv +
                ", testJavaEnv=" + testJavaEnv +
                ", scm=" + scm +
                ", developers=" + developers +
                ", licenses=" + licenses +
                ", properties=" + properties +
                '}';
    }
}

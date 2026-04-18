package jbuild.groovy;

import org.codehaus.groovy.tools.groovydoc.gstringTemplates.GroovyDocTemplateInfo;

import java.util.Collection;
import java.util.List;

public final class GroovydocToolArguments {

    public final String[] sourceDirs;
    public final Collection<String> sourceFiles;
    public final String outputDir;
    public final String[] docTemplates;
    public final String[] packageTemplates;
    public final String[] classTemplates;

    public GroovydocToolArguments(String[] sourceDirs,
                                  Collection<String> sourceFiles,
                                  String outputDir,
                                  String[] docTemplates,
                                  String[] packageTemplates,
                                  String[] classTemplates) {
        this.sourceDirs = sourceDirs;
        this.sourceFiles = sourceFiles;
        this.outputDir = outputDir;
        this.docTemplates = docTemplates;
        this.packageTemplates = packageTemplates;
        this.classTemplates = classTemplates;
    }

    public GroovydocToolArguments(String[] sourceDirs,
                                  List<String> sourceFiles,
                                  String outputDir) {
        this(sourceDirs, sourceFiles, outputDir,
                GroovyDocTemplateInfo.DEFAULT_DOC_TEMPLATES,
                GroovyDocTemplateInfo.DEFAULT_PACKAGE_TEMPLATES,
                GroovyDocTemplateInfo.DEFAULT_CLASS_TEMPLATES);
    }
}

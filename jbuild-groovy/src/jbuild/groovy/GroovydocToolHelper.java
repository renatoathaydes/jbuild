package jbuild.groovy;

import groovy.lang.GroovySystem;
import org.codehaus.groovy.tools.groovydoc.ClasspathResourceManager;
import org.codehaus.groovy.tools.groovydoc.FileOutputTool;
import org.codehaus.groovy.tools.groovydoc.GroovyDocTool;
import org.codehaus.groovy.tools.groovydoc.ResourceManager;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * This class is instantiated and invoked by reflection by {@code jbuild.commands.GroovyDocInvoker}.
 * <p>
 * It creates the project's groovydocs using the {@link GroovyDocTool}, from the Groovy project.
 */
public final class GroovydocToolHelper implements Callable<Void> {

    private final GroovydocToolArguments args;

    public GroovydocToolHelper(GroovydocToolArguments args) {
        this.args = args;
    }

    @Override
    public Void call() throws Exception {
        var tool = createGroovyDocTool();

        tool.add(args.sourceFiles);

        var output = new FileOutputTool();
        tool.renderToOutput(output, args.outputDir);

        return null;
    }

    /**
     * This class is compiled with Groovy 5. To support Groovy 4, we need to be able to instantiate
     * {@link GroovyDocTool} with the old constructor that did not have the {@code javaVersion} parameter
     * (the second last String parameter of the constructor).
     *
     * @return the groovyDoc tool.
     */
    private GroovyDocTool createGroovyDocTool() throws Exception {
        var resourceManager = new ClasspathResourceManager();

        int groovyVersion;
        try {
            groovyVersion = Integer.parseInt(GroovySystem.getShortVersion().substring(0, 1));
        } catch (NumberFormatException e) {
            // do not fail because of this!
            groovyVersion = 5;
        }

        if (groovyVersion < 5) {
            return createGroovyDocToolForGroovy4(resourceManager);
        }

        return new GroovyDocTool(
                resourceManager,
                args.sourceDirs,
                args.docTemplates,
                args.packageTemplates,
                args.classTemplates,
                Collections.emptyList(),
                null,
                new Properties()
        );
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private GroovyDocTool createGroovyDocToolForGroovy4(ResourceManager resourceManager) throws Exception {
        return GroovyDocTool.class.getConstructor(ResourceManager.class,
                        String[].class, String[].class, String[].class, String[].class, List.class, Properties.class)
                .newInstance(resourceManager,
                        args.sourceDirs,
                        args.docTemplates,
                        args.packageTemplates,
                        args.classTemplates,
                        Collections.emptyList(),
                        new Properties());
    }
}

package jbuild.groovy;

import org.codehaus.groovy.tools.groovydoc.ClasspathResourceManager;
import org.codehaus.groovy.tools.groovydoc.FileOutputTool;
import org.codehaus.groovy.tools.groovydoc.GroovyDocTool;

import java.util.Collections;
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
        var resourceManager = new ClasspathResourceManager();

        var tool = new GroovyDocTool(
                resourceManager,
                args.sourceDirs,
                args.docTemplates,
                args.packageTemplates,
                args.classTemplates,
                Collections.emptyList(),
                null,
                new Properties()
        );

        tool.add(args.sourceFiles);

        var output = new FileOutputTool();
        tool.renderToOutput(output, args.outputDir);

        return null;
    }
}

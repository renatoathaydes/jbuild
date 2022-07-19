package jbuild.script;

import jbuild.errors.JBuildException;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.CharArrayReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ServiceLoader;

import static jbuild.errors.JBuildException.ErrorCause.USER_INPUT;

public final class ScriptRunner {

    private final ScriptEngine engine;
    private final JBuildLangProvider jBuildLangProvider;

    public ScriptRunner(String extension) {
        var loader = ServiceLoader.load(JBuildLangProvider.class);
        ScriptEngine engine = null;
        JBuildLangProvider jBuildLangProvider = null;
        for (var provider : loader) {
            if (provider.extensions().contains(extension)) {
                jBuildLangProvider = provider;
                engine = new ScriptEngineManager().getEngineByName(provider.language());
                break;
            }
        }
        if (engine == null) {
            throw new JBuildException("Cannot find JBuild language provider for file extension " + extension, USER_INPUT);
        }

        this.engine = engine;
        this.jBuildLangProvider = jBuildLangProvider;
        init();
    }

    private void init() {
        getBindings().put("install", jBuildLangProvider.installFunction());
    }

    public JBuildConfig run(String script) throws ScriptException {
        return run(new CharArrayReader(script.toCharArray()));
    }

    public JBuildConfig run(Reader script) throws ScriptException {
        engine.eval(script);
        var bindings = getBindings();
        return () -> jBuildLangProvider.getInstallDependencies(bindings);
    }

    private Bindings getBindings() {
        return engine.getBindings(ScriptContext.ENGINE_SCOPE);
    }

    public static ScriptRunner forFile(Path path) {
        var fileName = path.getFileName().toString();
        var dotIdx = fileName.lastIndexOf('.');
        if (dotIdx < 0) {
            throw new IllegalArgumentException("File name must have an extension: " + fileName);
        }
        var ext = fileName.substring(dotIdx + 1);
        return new ScriptRunner(ext);
    }

}

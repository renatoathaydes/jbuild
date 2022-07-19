package jbuild.script;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import javax.script.Bindings;
import java.util.LinkedHashSet;
import java.util.Set;

public final class LuaJBuildLangProvider implements JBuildLangProvider {

    private final Set<String> dependencies = new LinkedHashSet<>();

    @Override
    public Set<String> extensions() {
        return Set.of("lua", "luaj");
    }

    @Override
    public String language() {
        return "luaj";
    }

    @Override
    public Object installFunction() {
        return new VarArgFunction() {
            @Override
            public LuaValue invoke(Varargs arg) {
                for (var i = 0; i < arg.narg(); i++) {
                    dependencies.add(arg.arg(i + 1).checkjstring());
                }
                return LuaValue.NIL;
            }
        };
    }

    @Override
    public Set<String> getInstallDependencies(Bindings bindings) {
        return dependencies;
    }
}

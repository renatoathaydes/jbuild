package tests;

import jbuild.script.ScriptRunner;
import org.junit.jupiter.api.Test;

import javax.script.ScriptException;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class ScriptTest {

    @Test
    void canRunLuaScript() throws ScriptException {
        var runner = ScriptRunner.forFile(Paths.get("jbuild.lua"));
        var config = runner.run("install('a:b:c', 'd:e:f')");
        assertThat(config.getInstallDependencies()).containsExactlyInAnyOrder("a:b:c", "d:e:f");
    }
}

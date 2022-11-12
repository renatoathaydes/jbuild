package jbuild.cli;

import jbuild.errors.JBuildException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static jbuild.util.TextUtils.LINE_END;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FetchOptionsTest {

    @Test
    void canParseFetchOptions() {
        verifyOptions(FetchOptions.parse(List.of(), false),
                ".", List.of());
        verifyOptions(FetchOptions.parse(List.of("foo"), false),
                ".", List.of("foo"));
        verifyOptions(FetchOptions.parse(List.of("-d", "target"), false),
                "target", List.of());
        verifyOptions(FetchOptions.parse(List.of("-d", "target", "lib"), false),
                "target", List.of("lib"));
        verifyOptions(FetchOptions.parse(List.of("lib1", "--directory", "target", "lib2"), false),
                "target", List.of("lib1", "lib2"));
    }

    @Test
    void mustNotProvideDirectoryOptionMoreThanOnce() {
        assertThatThrownBy(() -> FetchOptions.parse(List.of("-d", "o", "-d", "o"), false))
                .isInstanceOf(JBuildException.class)
                .hasMessage("fetch option -d must not appear more than once");

        assertThatThrownBy(() -> FetchOptions.parse(List.of("-d", "o", "--directory", "f"), false))
                .isInstanceOf(JBuildException.class)
                .hasMessage("fetch option --directory must not appear more than once");
    }

    @Test
    void mustNotRecognizeUnknownOption() {
        assertThatThrownBy(() -> FetchOptions.parse(List.of("-f"), true))
                .isInstanceOf(JBuildException.class)
                .hasMessage("invalid fetch option: -f." + LINE_END +
                        "Run jbuild --help for usage.");

        assertThatThrownBy(() -> FetchOptions.parse(List.of("-d", "o", "--nothing"), false))
                .isInstanceOf(JBuildException.class)
                .hasMessage("invalid fetch option: --nothing.");
    }

    private void verifyOptions(FetchOptions options,
                               String outDir,
                               List<String> artifacts) {
        assertThat(options.outDir).isEqualTo(outDir);
        assertThat(options.artifacts).containsExactlyElementsOf(artifacts);
    }
}

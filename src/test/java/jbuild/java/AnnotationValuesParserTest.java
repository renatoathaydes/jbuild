package jbuild.java;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationValuesParserTest {

    final AnnotationValuesParser parser = new AnnotationValuesParser();

    @Test
    void canParseAnnotationWithOneField() {
        var example = "  0: #60(#61=s#62)\n" +
                "    jbuild.api.JbTaskInfo(\n" +
                "      name=\"my task\"\n" +
                "    )\n" +
                "ignore!";

        var results = parser.parse(example.lines().iterator());

        assertThat(results).hasSize(1);
        var result = results.iterator().next();
        assertThat(result.name).isEqualTo("jbuild.api.JbTaskInfo");
        assertThat(result.getString("name")).isEqualTo("my task");
        assertThat(result.getValues().keySet()).containsExactly("name");
    }

    @Test
    void canParseAnnotationWithSeveralFields() {
        var example = "" +
                "  0: #16(#17=s#18,#19=[s#20,s#21],#22=@#23(#24=I#25,#17=s#26))\n" +
                "    jbuild.api.JbTaskInfo(\n" +
                "      name=\"my-name\"\n" +
                "      inputs=[\"in\",\"other-in\"]\n" +
                "      phase=@jbuild.api.CustomTaskPhase(\n" +
                "        index=222\n" +
                "        name=\"cust-phase\"\n" +
                "      )\n" +
                "    )\n" +
                "FOO\n" +
                "BAR";

        var results = parser.parse(example.lines().iterator());

        assertThat(results).hasSize(1);
        var result = results.iterator().next();
        assertThat(result.name).isEqualTo("jbuild.api.JbTaskInfo");
        assertThat(result.getString("name")).isEqualTo("my-name");
        assertThat(result.getAllStrings("inputs")).isEqualTo(List.of("in", "other-in"));

        var customPhase = result.getSub("phase");
        assertThat(customPhase.name).isEqualTo("jbuild.api.CustomTaskPhase");
        assertThat(customPhase.getInt("index")).isEqualTo(222);
        assertThat(customPhase.getString("name")).isEqualTo("cust-phase");

    }

}

package jbuild.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class TextUtilsTest {

    @Test
    void findFirstNonBlank() {
        assertThat(TextUtils.firstNonBlank("", "foo")).isEqualTo("foo");
        assertThat(TextUtils.firstNonBlank(null, "foo")).isEqualTo("foo");
        assertThat(TextUtils.firstNonBlank(null, "")).isEqualTo("");
        assertThat(TextUtils.firstNonBlank(null, null)).isNull();
        assertThat(TextUtils.firstNonBlank("bar", "foo")).isEqualTo("bar");
    }

    @Test
    void canShowDurationNicely() {
        assertThat("" + TextUtils.durationText(Duration.ZERO))
                .isEqualTo("0 ms");
        assertThat("" + TextUtils.durationText(Duration.ofSeconds(1)))
                .isEqualTo("1 sec");
        assertThat("" + TextUtils.durationText(Duration.ofMillis(1)))
                .isEqualTo("1 ms");
        assertThat("" + TextUtils.durationText(Duration.ofMillis(1500)))
                .isEqualTo("1 sec, 500 ms");
        assertThat("" + TextUtils.durationText(Duration.ofMinutes(63)))
                .isEqualTo("1 hr, 3 min");
        assertThat("" + TextUtils.durationText(Duration.ofMinutes(5).plus(Duration.ofMillis(3))))
                .isEqualTo("5 min, 3 ms");
        assertThat("" + TextUtils.durationText(Duration.ofMillis(
                (23 * 60L * 60L * 1000L) +
                        (42 * 60L * 1000L) +
                        (37 * 1000L) +
                        543)))
                .isEqualTo("23 hr, 42 min, 37 sec, 543 ms");

        // over a day, we degrade to Duration.toString
        assertThat("" + TextUtils.durationText(Duration.ofDays(1)))
                .isEqualTo("PT24H");
        assertThat("" + TextUtils.durationText(Duration.ofDays(35).plus(Duration.ofMillis(15))))
                .isEqualTo("PT840H0.015S");
    }
}

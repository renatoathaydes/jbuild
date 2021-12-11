package jbuild.util;

import jbuild.maven.ArtifactKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactKeyTest {

    @Test
    void cachesKeys() {
        assertThat(ArtifactKey.of("g", "a"))
                .isSameAs(ArtifactKey.of("g", "a"));
        assertThat(ArtifactKey.of("g", "b"))
                .isNotSameAs(ArtifactKey.of("g", "a"));
        assertThat(ArtifactKey.of("h", "b"))
                .isSameAs(ArtifactKey.of("h", "b"));
    }

    @Test
    void canPutInHashMap() {
        var map = new HashMap<ArtifactKey, String>();

        map.put(ArtifactKey.of("g", "a"), "ga");
        map.put(ArtifactKey.of("g", "b"), "gb");
        map.put(ArtifactKey.of("g", "c"), "gc");
        map.put(ArtifactKey.of("h", "a"), "ha");
        map.put(ArtifactKey.of("h", "b"), "hb");

        assertThat(map).containsOnlyKeys(
                ArtifactKey.of("g", "a"),
                ArtifactKey.of("g", "b"),
                ArtifactKey.of("g", "c"),
                ArtifactKey.of("h", "a"),
                ArtifactKey.of("h", "b")
        );

        assertThat(map).extractingByKey(ArtifactKey.of("g", "a")).isEqualTo("ga");
        assertThat(map).extractingByKey(ArtifactKey.of("g", "b")).isEqualTo("gb");
        assertThat(map).extractingByKey(ArtifactKey.of("g", "c")).isEqualTo("gc");
        assertThat(map).extractingByKey(ArtifactKey.of("h", "a")).isEqualTo("ha");
        assertThat(map).extractingByKey(ArtifactKey.of("h", "b")).isEqualTo("hb");
    }

    @Test
    void concurrencySafe() throws InterruptedException {
        var maxCount = 100;

        Function<ArtifactKey[], Runnable> createRunnable = (keys) -> () -> {
            int index = 0;
            for (int i = 0; i < maxCount; i++) {
                for (int j = maxCount; j > 0; j--) {
                    keys[index++] = ArtifactKey.of(Integer.toString(i), Integer.toString(j));
                }
            }
        };

        ArtifactKey[] key1 = new ArtifactKey[maxCount * maxCount];
        ArtifactKey[] key2 = new ArtifactKey[maxCount * maxCount];

        var t1 = new Thread(createRunnable.apply(key1));
        var t2 = new Thread(createRunnable.apply(key2));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // assuming concurrent access works, all expected keys should've been created as expected
        var index = 0;
        for (int i = 0; i < maxCount; i++) {
            for (int j = maxCount; j > 0; j--) {
                assertThat(key1[index]).isSameAs(key2[index]);
                assertThat(key1[index]).isSameAs(ArtifactKey.of(Integer.toString(i), Integer.toString(j)));
                index++;
            }
        }
    }
}

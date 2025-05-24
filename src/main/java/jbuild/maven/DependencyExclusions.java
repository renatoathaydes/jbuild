package jbuild.maven;

import jbuild.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public final class DependencyExclusions {

    public static final DependencyExclusions EMPTY = new DependencyExclusions(Set.of(), Map.of());

    public static final class PatternUsage {
        private final Pattern pattern;
        private final AtomicInteger usages = new AtomicInteger(0);

        PatternUsage(Pattern pattern) {
            this.pattern = pattern;
        }

        public int getUsages() {
            return usages.get();
        }

        boolean matches(CharSequence input) {
            var match = pattern.matcher(input).matches();
            if (match) {
                usages.incrementAndGet();
            }
            return match;
        }

        @Override
        public String toString() {
            return pattern.toString();
        }

        public PatternUsage adding(PatternUsage other) {
            usages.addAndGet(other.usages.get());
            return this;
        }
    }

    public static final class ExclusionsWithUsage {
        public final List<PatternUsage> globalExclusions;
        public final Map<String, List<PatternUsage>> exclusionsByArtifact;

        private ExclusionsWithUsage(List<PatternUsage> globalExclusions,
                                    Map<String, List<PatternUsage>> exclusionsByArtifact) {
            this.globalExclusions = globalExclusions;
            this.exclusionsByArtifact = exclusionsByArtifact;
        }

        public boolean isEmpty() {
            return globalExclusions.isEmpty() && exclusionsByArtifact.isEmpty();
        }

        public List<PatternUsage> get(String artifact) {
            return exclusionsByArtifact.get(artifact);
        }

        public ExclusionsWithUsage withGlobalExclusions(List<PatternUsage> exclusions) {
            if (exclusions.isEmpty()) return this;
            var globalExclusions = merge(this.globalExclusions, exclusions);
            return new DependencyExclusions.ExclusionsWithUsage(globalExclusions, exclusionsByArtifact);
        }

        private static List<PatternUsage> merge(List<PatternUsage> first, List<PatternUsage> second) {
            if (first.isEmpty()) return second;
            if (second.isEmpty()) return first;
            var firstMap = first.stream().collect(toMap(p -> p.pattern, Function.identity()));
            var secondMap = second.stream().collect(toMap(p -> p.pattern, Function.identity()));
            var result = new ArrayList<PatternUsage>(firstMap.size() + secondMap.size());
            for (var entry : firstMap.entrySet()) {
                var patternUsage = entry.getValue();
                var secondPatternUsage = secondMap.remove(entry.getKey());
                if (secondPatternUsage == null) {
                    result.add(patternUsage);
                } else {
                    result.add(patternUsage.adding(secondPatternUsage));
                }
            }
            for (var entry : secondMap.entrySet()) {
                var patternUsage = entry.getValue();
                var secondPatternUsage = firstMap.get(entry.getKey());
                if (secondPatternUsage == null) {
                    result.add(patternUsage);
                } else {
                    result.add(secondPatternUsage.adding(patternUsage));
                }
            }
            return result;
        }
    }

    private final Set<Pattern> globalExclusions;
    private final Map<String, Set<Pattern>> exclusionsByArtifact;

    public DependencyExclusions(Set<Pattern> globalExclusions,
                                Map<String, Set<Pattern>> exclusionsByArtifact) {
        this.globalExclusions = globalExclusions;
        this.exclusionsByArtifact = exclusionsByArtifact;
    }

    public Map<String, Set<Pattern>> getExclusions() {
        var map = new HashMap<String, Set<Pattern>>(exclusionsByArtifact.size() + 1);
        map.putAll(exclusionsByArtifact);
        map.put("", globalExclusions);
        return map;
    }

    public boolean isEmpty() {
        return globalExclusions.isEmpty() && exclusionsByArtifact.isEmpty();
    }

    public ExclusionsWithUsage withUsage() {
        return new ExclusionsWithUsage(
                usagesFrom(this.globalExclusions),
                CollectionUtils.mapValues(this.exclusionsByArtifact, DependencyExclusions::usagesFrom));
    }

    private static List<PatternUsage> usagesFrom(Collection<Pattern> patterns) {
        return patterns.stream()
                .map(PatternUsage::new)
                .collect(Collectors.toList());
    }
}

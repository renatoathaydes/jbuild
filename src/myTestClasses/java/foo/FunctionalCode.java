package foo;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionalCode {
    private final ExampleLogger log;

    public FunctionalCode(ExampleLogger log) {
        this.log = log;
    }

    public IntSummaryStatistics countLengths(List<Zort> list) {
        return list.stream()
                .map(z -> z.bar == null ? Zort.createBar().toString() : z.bar.toString())
                .mapToInt(String::length)
                .summaryStatistics();
    }

    public List<String> filter(List<SomeEnum> enums) {
        return enums.stream().filter(e -> {
                    if (e == SomeEnum.SOMETHING) return true;
                    if (e == null) {
                        log.info("null enum");
                    }
                    return false;
                }).map(SomeEnum::toString)
                .peek(log::debug)
                .collect(Collectors.toList());
    }

    public void logLengthsStats(List<Zort> list) {
        log.info(countLengths(list).toString());
    }

}

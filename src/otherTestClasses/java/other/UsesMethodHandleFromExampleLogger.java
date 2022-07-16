package other;

import foo.ExampleLogger;

import java.util.List;
import java.util.stream.Collectors;

public class UsesMethodHandleFromExampleLogger {

    ExampleLogger exampleLogger;

    @SuppressWarnings("SimplifyStreamApiCallChains")
    void foo(List<?> items) {
        // non-static method handle
        items.stream()
                .map(Object::toString)
                .forEach(exampleLogger::debug);

        // static method handle
        items.stream()
                .collect(Collectors.toSet());
    }

}

package other;

import foo.ExampleLogger;

import java.util.List;

public class UsesMethodHandleFromExampleLogger {

    ExampleLogger exampleLogger;

    void foo(List<?> items) {
        items.stream()
                .map(Object::toString)
                .forEach(exampleLogger::debug);
    }

}

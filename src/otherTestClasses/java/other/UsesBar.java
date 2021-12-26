package other;

import foo.Bar;

public class UsesBar {
    void foo() {
        // method ref
        new Bar();
    }
}

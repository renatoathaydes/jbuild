package other;

import foo.Something;
import foo.SomethingSpecific;

public class CallsSuperMethod {
    String callSuperOf(SomethingSpecific somethingSpecific) {
        return somethingSpecific.some();
    }

    String call(Something something) {
        return something.some();
    }
}

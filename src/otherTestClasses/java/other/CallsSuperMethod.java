package other;

import foo.SomethingSpecific;

public class CallsSuperMethod {
    String callSuperOf(SomethingSpecific somethingSpecific) {
        return somethingSpecific.some();
    }
}

package other;

import foo.FunctionalCode;

public class UsesArrayOfFunctionalCode {

    void doNothing(FunctionalCode[] codes) {
    }

    Object[] makesArray() {
        var codes = new FunctionalCode[4];
        return codes;
    }

}

package other;

import foo.Bar;
import foo.Zort;

public class ReadsFieldOfZort {
    void z(Zort zort) {
        System.out.println(zort.bar);
    }

    // no code reference to Bar, only method parameters
    void b(Bar b) {
    }

    // no code reference to Bar, only method return type
    Bar c(int i) {
        return null;
    }
}

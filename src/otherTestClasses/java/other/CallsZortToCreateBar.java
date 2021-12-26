package other;

import foo.Bar;
import foo.Zort;

public class CallsZortToCreateBar {
    Object x = Zort.getBar(new Bar());
}

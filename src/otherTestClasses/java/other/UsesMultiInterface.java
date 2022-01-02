package other;

import foo.MultiInterface;

public class UsesMultiInterface {

    void callJavaMethodViaInterface(MultiInterface multiInterface) {
        multiInterface.run();
    }

}

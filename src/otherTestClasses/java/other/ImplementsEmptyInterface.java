package other;

import foo.EmptyInterface;

public class ImplementsEmptyInterface implements EmptyInterface, Runnable {
    @Override
    public void run() {
    }
}

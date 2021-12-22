package foo;

public class Zort {
    static {
        Bar b = new Bar();
        System.out.println(b);
    }

    static Bar createBar() {
        return new Bar();
    }

    Bar bar;

    public static Bar getBar(Bar bar) {
        return bar;
    }
}

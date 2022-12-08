package tests;

public class CalledInRpcMainTest {

    public String greeting() {
        return "hello";
    }

    public int takeSomeArgs(boolean b, int i, Object... more) {
        return (b ? 1 : 0) + i + more.length;
    }

}

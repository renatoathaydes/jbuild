public class Hello {

    public static final String CONST = "";

    public final boolean isOk = true;
    private final String message;
    float aFloat;
    protected int protectedInt;

    public Hello(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean foo() {
        return isOk;
    }

    float theFloat(float a, long b) {
        return aFloat + a * ((float) b);
    }

    private void aPrivateMethod() {

    }
}
public class Hello {

    public static final String CONST = "";

    public final boolean isOk = true;
    private final String message;

    public Hello(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean foo() {
        return isOk;
    }
}
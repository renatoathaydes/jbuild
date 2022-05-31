package recursion;

public class Ping {
    void ping(Pong pong) {
        pong.pong(this);
    }
}

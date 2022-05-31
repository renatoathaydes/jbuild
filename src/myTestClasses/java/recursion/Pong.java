package recursion;

public class Pong {
    void pong(Ping ping) {
        ping.ping(this);
    }
}

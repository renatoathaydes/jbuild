package recursion;

public class TicTacToe {

    void go() {
        new Tic().tic();
    }

    public static class Tic {
        void tic() {
            new Tac().tac();
        }
    }

    public static class Tac {
        void tac() {
            new Toe().toe();
        }
    }

    public static class Toe {
        void toe() {
            new Tic().tic();
        }
    }
}

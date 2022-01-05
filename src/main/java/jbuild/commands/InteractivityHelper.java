package jbuild.commands;

import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;

final class InteractivityHelper {

    static boolean askYesOrNoQuestion(String question) {
        var scanner = new Scanner(System.in, UTF_8);
        boolean result;
        while (true) {
            String answer;
            System.out.print(question + " [y/n]? ");
            answer = scanner.nextLine();
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                result = true;
                break;
            }
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")) {
                result = false;
                break;
            }
            System.err.println("Invalid answer! Please enter 'y' or 'n'.");
        }
        System.out.println();
        return result;
    }

}

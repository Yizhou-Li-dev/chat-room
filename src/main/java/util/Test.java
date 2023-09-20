package util;

import java.util.Scanner;

public class Test {
    
    private static void myMethod() {
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
        scanner.close();
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
        scanner.close();
        myMethod();
    }
}

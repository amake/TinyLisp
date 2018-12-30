package org.tinylisp.repl;

import org.tinylisp.engine.Engine;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class App {

    private final Engine mEngine = new Engine();
    private final Engine.TLEnvironment mEnv = Engine.defaultEnvironment();
    private final Scanner mScanner;
    private final PrintStream mOut;

    public App() {
        this(System.in, System.out);
    }

    public App(InputStream in, PrintStream out) {
        mScanner = new Scanner(in);
        mOut = out;
    }

    public void start() {
        while (true) {
            String input = prompt();
            if (input != null) {
                try {
                    Engine.TLExpression result = mEngine.execute(input, mEnv);
                    mEnv.put(Engine.TLSymbolExpression.of("_"), result);
                    mOut.println(result == null ? "" : result);
                } catch (Exception ex) {
                    mOut.println(ex);
                }
            }
        }
    }

    private String prompt() {
        mOut.print(">>> ");
        try {
            return mScanner.nextLine();
        } catch (NoSuchElementException ex) {
            System.exit(0);
            return null;
        }
    }

    public static void main(String[] args) {
        new App().start();
    }
}

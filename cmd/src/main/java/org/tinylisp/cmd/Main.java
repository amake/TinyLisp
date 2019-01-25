package org.tinylisp.cmd;

import org.tinylisp.engine.Engine;

import java.nio.file.Paths;

public class Main {

    public static Object execute(String program) throws Exception {
        Engine engine = new Engine();
        Engine.TLEnvironment env = Engine.defaultEnvironment();
        return engine.execute(program, env);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            Object result = execute(Util.readString(Paths.get(args[0])));
            System.out.println(result);
        } else if (System.in.available() > 0) {
            Object result = execute(Util.readString(System.in));
            System.out.println(result);
        } else {
            new Repl().start();
        }
    }
}

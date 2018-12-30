package org.tinylisp.repl;

import org.tinylisp.engine.Engine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static Object execute(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String program = new String(bytes, StandardCharsets.UTF_8);
        Engine engine = new Engine();
        Engine.TLEnvironment env = Engine.defaultEnvironment();
        return engine.execute(program, env);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            Object result = execute(Paths.get(args[0]));
            System.out.println(result);
        } else {
            new Repl().start();
        }
    }
}

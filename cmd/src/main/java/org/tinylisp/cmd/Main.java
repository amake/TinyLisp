package org.tinylisp.cmd;

import org.tinylisp.engine.Engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static Object execute(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        String program = new String(bytes, StandardCharsets.UTF_8);
        return execute(program);
    }

    public static Object execute(InputStream stream) throws Exception {
        String program = readString(stream);
        return execute(program);
    }

    public static Object execute(String program) throws Exception {
        Engine engine = new Engine();
        Engine.TLEnvironment env = Engine.defaultEnvironment();
        return engine.execute(program, env);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            Object result = execute(Paths.get(args[0]));
            System.out.println(result);
        } else if (System.in.available() > 0) {
            Object result = execute(System.in);
            System.out.println(result);
        } else {
            new Repl().start();
        }
    }

    private static String readString(InputStream is) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}

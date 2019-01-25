package org.tinylisp.cmd;

import org.tinylisp.formatter.Formatter;

import java.nio.file.Paths;

public class Fmt {

    public static String format(String program) {
        Formatter formatter = new Formatter();
        return formatter.format(program);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            Object result = format(Util.readString(Paths.get(args[0])));
            System.out.println(result);
        } else if (System.in.available() > 0) {
            Object result = format(Util.readString(System.in));
            System.out.println(result);
        } else {
            System.out.println("Usage: tlfmt <file>");
            System.exit(1);
        }
    }
}

package org.tinylisp.repl;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.tinylisp.engine.Engine;

import java.io.IOException;

public class Repl {

    private final Terminal mTerminal;
    private final LineReader mLineReader;
    private final Engine mEngine = new Engine();
    private final Engine.TLEnvironment mEnv = Engine.defaultEnvironment();

    public Repl() throws IOException {
        mTerminal = TerminalBuilder.builder()
                .name("TinyLisp terminal")
                .system(true)
                .build();
        mLineReader = LineReaderBuilder.builder()
                .appName("TinyLisp")
                .terminal(mTerminal)
                .build();
    }

    public void start() {
        mTerminal.writer().printf("TinyLisp %s\n", Engine.VERSION);
        while (true) {
            String input = prompt();
            if (input != null) {
                try {
                    Engine.TLExpression result = mEngine.execute(input, mEnv);
                    mEnv.put(Engine.TLSymbolExpression.of("_"), result);
                    mTerminal.writer().println(result == null ? "" : result);
                } catch (Exception ex) {
                    mTerminal.writer().println(ex);
                }
            }
        }
    }

    private String prompt() {
        try {
            return mLineReader.readLine(">>> ");
        } catch (UserInterruptException ex) {
            // Ignore
        } catch (EndOfFileException ex) {
            System.exit(0);
        }
        return null;
    }
}

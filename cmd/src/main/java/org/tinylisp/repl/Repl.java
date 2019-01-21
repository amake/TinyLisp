package org.tinylisp.repl;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.tinylisp.engine.Engine;

import java.io.IOException;

public class Repl {

    private static class TinyLispParser extends DefaultParser {
        @Override
        public boolean isDelimiterChar(CharSequence buffer, int pos) {
            char c = buffer.charAt(pos);
            return '(' == c || ')' == c || '[' == c || ']' == c || super.isDelimiterChar(buffer, pos);
        }
    }

    private final Terminal mTerminal;
    private final LineReader mLineReader;
    private final Engine mEngine = new Engine();
    private Engine.TLEnvironment mEnv;
    private final Completer mCompleter = (reader, line, candidates) -> {
        String token = line.word().substring(0, line.wordCursor());
        for (String completion : mEnv.complete(token)) {
            candidates.add(new Candidate(completion));
        }
    };

    public Repl() throws IOException {
        mTerminal = TerminalBuilder.builder()
                .name("TinyLisp terminal")
                .system(true)
                .build();
        mLineReader = LineReaderBuilder.builder()
                .appName("TinyLisp")
                .terminal(mTerminal)
                .parser(new TinyLispParser())
                .completer(mCompleter)
                .build();
        initEnvironment();
    }

    protected void initEnvironment() {
        mEnv = Engine.defaultEnvironment();
        mEnv.put(Engine.TLSymbolExpression.of("clear"), new Engine.TLFunction() {
            @Override
            public Engine.TLExpression invoke(Engine.TLListExpression args) {
                clear();
                return Engine.expressionOf(null);
            }
        });
        mEnv.put(Engine.TLSymbolExpression.of("reset"), new Engine.TLFunction() {
            @Override
            public Engine.TLExpression invoke(Engine.TLListExpression args) {
                clear();
                initEnvironment();
                return Engine.expressionOf(null);
            }
        });
    }

    public void clear() {
        if (mLineReader instanceof LineReaderImpl) {
            ((LineReaderImpl) mLineReader).clearScreen();
        }
    }

    public void start() {
        mTerminal.writer().printf("TinyLisp %s\n", Engine.VERSION);
        while (true) {
            String input = prompt();
            if (input != null && !input.isEmpty()) {
                try {
                    Engine.TLExpression result = mEngine.execute(input, mEnv);
                    mEnv.put(Engine.TLSymbolExpression.of("_"), result);
                    mTerminal.writer().println(result.getValue() == null ? "" : result);
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

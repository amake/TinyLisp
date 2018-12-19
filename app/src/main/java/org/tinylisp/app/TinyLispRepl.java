package org.tinylisp.app;

import org.apache.commons.lang3.StringUtils;
import org.tinylisp.engine.Engine;

import java.lang.reflect.Method;
import java.util.*;

public class TinyLispRepl {

    private interface Predicate<T> {
        boolean test(T value);
    }

    public interface PrintComponent {
        void print(String string);
        void clear();
    }

    private final PrintComponent mOutput;
    private Engine mEngine;
    private Engine.TLEnvironment mEnv;

    public TinyLispRepl(PrintComponent output) {
        mOutput = output;
    }

    public void init() {
        mOutput.clear();
        mOutput.print("TinyLisp\n");
        mEngine = new Engine();
        mEnv = Engine.defaultEnvironment();
    }

    /**
     * Complete the supplied partial input.
     * @param input
     * @return null if no completion could be computed; the recommended full input otherwise
     */
    public String complete(String input) {
        List<String> tokens = mEngine.tokenize(input);
        if (tokens.isEmpty()) {
            return null;
        }
        String stem = tokens.get(tokens.size() - 1);
        String leading = input.substring(0, input.length() - stem.length());
        List<String> candidates = completeSymbol(stem);
        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return leading + candidates.get(0) + " ";
        } else {
            printCompletionHelp(stem, candidates);
            String commonPrefix = StringUtils.getCommonPrefix(candidates.toArray(new String[0]));
            return commonPrefix.isEmpty() ? null : leading + commonPrefix;
        }
    }

    private void printCompletionHelp(String stem, List<String> candidates) {
        if (stem.isEmpty()) {
            mOutput.print("All symbols:\n");
        } else {
            mOutput.print("Symbols starting with ");
            mOutput.print(stem);
            mOutput.print(":\n");
        }
        for (String candidate : candidates) {
            mOutput.print("    ");
            mOutput.print(candidate);
            mOutput.print("\n");
        }
    }

    private List<String> getSymbols(Predicate<String> filter) {
        List<String> result = new ArrayList<>(mEnv.size());
        for (Engine.TLSymbolExpression symbol : mEnv.keySet()) {
            String name = symbol.getValue();
            if (filter != null && filter.test(name)) {
                result.add(name);
            }
        }
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    public List<String> completeSymbol(final String prefix) {
        return getSymbols(new Predicate<String>() {
            @Override public boolean test(String value) {
                return value.startsWith(prefix);
            }
        });
    }

    public void execute(String input) {
        // echo
        echoInput(input);
        try {
            Engine.TLExpression result = mEngine.execute(input, mEnv);
            mEnv.put(Engine.TLSymbolExpression.of("_"), result);
            printObject(result);
        } catch (Exception ex) {
            printException(ex);
        }
    }

    private void printObject(Object object) {
        String repr;
        if (object instanceof int[]) {
            repr = Arrays.toString((int[]) object);
        } else {
            repr = object.toString();
        }
        mOutput.print(repr);
    }

    private void printException(Exception ex) {
        mOutput.print(findInterestingCause(ex).toString());
        ex.printStackTrace();
    }

    private Throwable findInterestingCause(Throwable throwable) {
        while (true) {
            if (throwable instanceof Engine.TLRuntimeException) {
                return throwable;
            }
            Throwable cause = throwable.getCause();
            if (cause == null) {
                return throwable;
            } else {
                throwable = cause;
            }
        }
    }

    private void echoInput(String input) {
        mOutput.print("\n> ");
        mOutput.print(input);
        mOutput.print("\n");
    }
}

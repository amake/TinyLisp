package org.tinylisp.engine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;

public class Engine {

    interface TLExpression {
    }

    interface TLFunction extends TLExpression {
        Object invoke(Object... args) throws Exception;
    }

    static class TLMethodFunction implements TLFunction {
        static TLMethodFunction of(Object object, Method method) {
            TLMethodFunction function = new TLMethodFunction();
            function.object = object;
            function.method = method;
            return function;
        }
        private Object object;
        private Method method;
        @Override public Object invoke(Object... args) throws Exception {
            return method.invoke(object, args);
        }
    }

    static class TLListExpression extends ArrayList<TLExpression> implements TLExpression {
        public TLListExpression() {
            super();
        }
        public TLListExpression(List<TLExpression> list) {
            super(list);
        }
    }

    abstract static class TLAtomExpression<T> implements TLExpression {
        protected T value;
        public T getValue() {
            return value;
        }
        @Override public String toString() {
            return Objects.toString(value);
        }
        @Override public int hashCode() {
            return Objects.hashCode(value);
        }
        @Override public boolean equals(Object o) {
            if (this.getClass().equals(o.getClass())) {
                return Objects.equals(this.value, ((TLAtomExpression<?>) o).value);
            } else {
                return false;
            }
        }

    }

    static class TLSymbolExpression extends TLAtomExpression<String> {
        static TLSymbolExpression of(String value) {
            TLSymbolExpression symbol = new TLSymbolExpression();
            symbol.value = value;
            return symbol;
        }
    }

    static class TLNumberExpression extends TLAtomExpression<Number> {
        static TLNumberExpression of(Number value) {
            TLNumberExpression number = new TLNumberExpression();
            number.value = value;
            return number;
        }
    }

    static class TLArrayExpression extends TLAtomExpression<Object[]> {
        static TLArrayExpression of(Object[] value) {
            TLArrayExpression array = new TLArrayExpression();
            array.value = value;
            return array;
        }
        @Override public String toString() {
            return Arrays.toString(value);
        }
    }

    static class TLJavaObjectExpression extends TLAtomExpression<Object> {
        static TLJavaObjectExpression of(Object value) {
            TLJavaObjectExpression jobj = new TLJavaObjectExpression();
            jobj.value = value;
            return jobj;
        }
    }

    static class TLEnvironment extends HashMap<TLSymbolExpression, TLExpression> {
        public TLEnvironment() {
            super();
        }
        public TLEnvironment(Map<TLSymbolExpression, TLExpression> env) {
            super(env);
        }
    }

    public TLAtomExpression apply(TLFunction function, List<Object> arguments, TLEnvironment environment) throws Exception {
        Object value = function.invoke(arguments.toArray(new Object[0]));
        return TLJavaObjectExpression.of(value);
    }

    public TLExpression evaluate(TLExpression object, TLEnvironment environment) throws Exception {
        if (object instanceof TLSymbolExpression) {
            TLSymbolExpression symbol = (TLSymbolExpression) object;
            return environment.get(symbol);
        } else if (object instanceof TLAtomExpression) {
            return object;
        } else if (object instanceof TLListExpression) {
            TLListExpression expression = (TLListExpression) object;
            // The first item in a list must be a function
            TLFunction function = (TLFunction) evaluate(expression.get(0), environment);
            List<Object> args = new ArrayList<>(expression.size() - 1);
            for (TLExpression exp : expression.subList(1, expression.size())) {
                // Function arguments must evaluate down to atoms
                TLAtomExpression atom = (TLAtomExpression) evaluate(exp, environment);
                args.add(atom.getValue());
            }
            return apply(function, args, environment);
        } else {
            throw new IllegalArgumentException("Can't evaluate " + object);
        }
    }

    public TLExpression parse(String input) {
        return readTokens(tokenize(input));
    }

    private TLExpression readTokens(ArrayList<String> tokens) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("End of token list");
        }
        String token = tokens.remove(0);
        if ("(".equals(token)) {
            TLListExpression expression = new TLListExpression();
            while (!")".equals(tokens.get(0))) {
                expression.add(readTokens(tokens));
            }
            tokens.remove(0);
            return expression;
        } else if ("[".equals(token)) {
            List<Object> values = new ArrayList<>();
            while (!"]".equals(tokens.get(0))) {
                // Arrays can only contain atoms
                TLAtomExpression atom = (TLAtomExpression) readTokens(tokens);
                values.add(atom.getValue());
            }
            tokens.remove(0);
            return TLArrayExpression.of(values.toArray());
        } else {
            return atomize(token);
        }
    }

    private TLExpression atomize(String token) {
        try {
            return TLNumberExpression.of(Integer.parseInt(token));
        } catch (NumberFormatException ex) {
            return TLSymbolExpression.of(token);
        }
    }

    public ArrayList<String> tokenize(String input) {
        String[] tokens = input.replace("(", " ( ")
            .replace(")", " ) ")
            .replace("[", " [ ")
            .replace("]", " ] ")
            .trim()
            .split("\\s+");
        ArrayList<String> filtered = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isEmpty()) {
                filtered.add(token);
            }
        }
        return filtered;
    }

    public Object execute(String program, TLEnvironment environment) throws Exception {
        // Final result of evaluation should be a Java object
        TLJavaObjectExpression result = (TLJavaObjectExpression) evaluate(parse(program), environment);
        return result.getValue();
    }
}

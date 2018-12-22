package org.tinylisp.engine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;

public class Engine {

    public interface TLExpression {
    }

    public interface TLFunction extends TLExpression {
        TLExpression invoke(TLListExpression args) throws Exception;
    }

    public static class TLMethodFunction implements TLFunction {
        static TLMethodFunction of(Object object, Method method) {
            TLMethodFunction function = new TLMethodFunction();
            function.object = object;
            function.method = method;
            return function;
        }
        private Object object;
        private Method method;
        @Override public TLExpression invoke(TLListExpression args) throws Exception {
            Object[] jargs = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                jargs[i] = ((TLAtomExpression) args.get(i)).getValue();
            }
            return TLJavaObjectExpression.of(method.invoke(object, jargs));
        }
    }

    public static class TLLambdaFunction implements TLFunction {
        static TLLambdaFunction of(TLListExpression params, TLListExpression body, TLEnvironment env, Engine engine) {
            TLLambdaFunction lambda = new TLLambdaFunction();
            lambda.params = params;
            lambda.body = body;
            lambda.env = new TLEnvironment(env);
            lambda.engine = engine;
            return lambda;
        }
        private TLListExpression params;
        private TLListExpression body;
        private TLEnvironment env;
        private Engine engine;
        @Override
        public TLExpression invoke(TLListExpression args) throws Exception {
            for (int i = 0; i < params.size(); i++) {
                TLSymbolExpression param = (TLSymbolExpression) params.get(i);
                TLExpression arg = args.get(i);
                env.put(param, arg);
            }
            return engine.evaluate(body, env);
        }
    }

    public static class TLListExpression extends ArrayList<TLExpression> implements TLExpression {
        public TLListExpression() {
            super();
        }
        public TLListExpression(List<TLExpression> list) {
            super(list);
        }
    }

    public abstract static class TLAtomExpression<T> implements TLExpression {
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

    public static class TLSymbolExpression extends TLAtomExpression<String> {
        static TLSymbolExpression of(String value) {
            TLSymbolExpression symbol = new TLSymbolExpression();
            symbol.value = value;
            return symbol;
        }
    }

    public static class TLNumberExpression extends TLAtomExpression<Number> {
        static TLNumberExpression of(Number value) {
            TLNumberExpression number = new TLNumberExpression();
            number.value = value;
            return number;
        }
    }

    public static class TLArrayExpression extends TLAtomExpression<Object[]> {
        static TLArrayExpression of(Object[] value) {
            TLArrayExpression array = new TLArrayExpression();
            array.value = value;
            return array;
        }
        @Override public String toString() {
            return Arrays.toString(value);
        }
    }

    public static class TLJavaObjectExpression extends TLAtomExpression<Object> {
        static TLJavaObjectExpression of(Object value) {
            TLJavaObjectExpression jobj = new TLJavaObjectExpression();
            jobj.value = value;
            return jobj;
        }
    }

    public static class TLEnvironment extends HashMap<TLSymbolExpression, TLExpression> {
        public TLEnvironment() {
            super();
        }
        public TLEnvironment(Map<TLSymbolExpression, TLExpression> env) {
            super(env);
        }
    }

    public TLExpression apply(TLFunction function, TLListExpression arguments) throws Exception {
        return function.invoke(arguments);
    }

    public TLExpression evaluate(TLExpression object, TLEnvironment environment) throws Exception {
        if (object instanceof TLSymbolExpression) {
            TLSymbolExpression symbol = (TLSymbolExpression) object;
            return environment.get(symbol);
        } else if (object instanceof TLAtomExpression) {
            return object;
        } else if (object instanceof TLListExpression) {
            TLListExpression expression = (TLListExpression) object;
            // The first item in a list must be a symbol
            TLExpression first = expression.get(0);
            if (first instanceof TLSymbolExpression && "set".equals(((TLSymbolExpression) first).getValue())) {
                TLSymbolExpression name = (TLSymbolExpression) expression.get(1);
                TLExpression value = expression.get(2);
                environment.put(name, evaluate(value, environment));
                return TLJavaObjectExpression.of(null);
            } else if (first instanceof TLSymbolExpression && "lambda".equals(((TLSymbolExpression) first).getValue())) {
                TLListExpression params = (TLListExpression) expression.get(1);
                TLListExpression body = (TLListExpression) expression.get(2);
                return TLLambdaFunction.of(params, body, environment, this);
            } else {
                // First item wasn't a special form so it must evaluate to a function
                TLFunction function = (TLFunction) evaluate(first, environment);
                TLListExpression args = new TLListExpression();
                for (TLExpression exp : expression.subList(1, expression.size())) {
                    args.add(evaluate(exp, environment));
                }
                return apply(function, args);
            }
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
        TLAtomExpression<?> result = (TLAtomExpression<?>) evaluate(parse(program), environment);
        return result.getValue();
    }
}

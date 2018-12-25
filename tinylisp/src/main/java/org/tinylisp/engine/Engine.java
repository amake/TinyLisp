package org.tinylisp.engine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Engine {

    public static TLAtomExpression<?> expressionOf(Object value) {
        if (value == null) {
            return TLJavaObjectExpression.of(null);
        } else if (value instanceof String) {
            return TLSymbolExpression.of((String) value);
        } else if (value instanceof Number) {
            return TLNumberExpression.of((Number) value);
        } else if (value.getClass().isArray()) {
            return TLArrayExpression.of(value);
        } else {
            return TLJavaObjectExpression.of(value);
        }
    }

    public interface TLExpression {
        Object getValue();
        boolean asBoolean();
    }

    public static abstract class TLFunction implements TLExpression {
        public abstract TLExpression invoke(TLListExpression args) throws Exception;
        @Override public Object getValue() {
            return this;
        }
        @Override public boolean asBoolean() {
            return true;
        }
    }

    public static class TLMethodFunction extends TLFunction {
        public static TLMethodFunction of(Object object, Method method) {
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
                jargs[i] = args.get(i).getValue();
            }
            return expressionOf(method.invoke(object, jargs));
        }
    }

    public static class TLLambdaFunction extends TLFunction {
        public static TLLambdaFunction of(TLListExpression params, TLListExpression body, TLEnvironment env, Engine engine) {
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
        @Override public boolean asBoolean() {
            return !isEmpty();
        }
        public List<Object> getValue() {
            List<Object> result = new ArrayList<>();
            for (TLExpression expression : this) {
                result.add(expression.getValue());
            }
            return result;
        }
    }

    public abstract static class TLAtomExpression<T> implements TLExpression {
        protected T value;
        public T getValue() {
            return value;
        }
        @Override public String toString() {
            return String.valueOf(value);
        }
        @Override public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
        @Override public boolean equals(Object o) {
            if (o != null && this.getClass().equals(o.getClass())) {
                Object oVal = ((TLAtomExpression<?>) o).value;
                return value == oVal || (value != null && value.equals(oVal));
            } else {
                return false;
            }
        }
        @Override public boolean asBoolean() {
            return value != null && !Boolean.FALSE.equals(value);
        }
    }

    public static class TLSymbolExpression extends TLAtomExpression<String> {
        public static TLSymbolExpression of(String value) {
            TLSymbolExpression symbol = new TLSymbolExpression();
            symbol.value = value;
            return symbol;
        }
    }

    public static class TLNumberExpression extends TLAtomExpression<Number> {
        public static TLNumberExpression of(Number value) {
            TLNumberExpression number = new TLNumberExpression();
            number.value = value;
            return number;
        }
    }

    public static class TLArrayExpression extends TLAtomExpression<Object> {
        public static TLArrayExpression of(Object value) {
            if (value == null || !value.getClass().isArray()) {
                throw new IllegalArgumentException("Value is not an array: " + value);
            }
            TLArrayExpression array = new TLArrayExpression();
            array.value = value;
            return array;
        }
        public static TLArrayExpression from(List<Object> values) {
            Class<?> arrayClass = getClass(values);
            Object value;
            if (Integer.class.equals(arrayClass)) {
                int[] intArray = new int[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    intArray[i] = (int) values.get(i);
                }
                value = intArray;
            } else if (Double.class.equals(arrayClass)) {
                double[] doubleArray = new double[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    doubleArray[i] = (double) values.get(i);
                }
                value = doubleArray;
            } else {
                value = values.toArray();
            }
            TLArrayExpression array = new TLArrayExpression();
            array.value = value;
            return array;
        }
        private static Class<?> getClass(List<Object> values) {
            if (values.isEmpty()) {
                return Object.class;
            }
            Class<?> result = values.get(0).getClass();
            for (Object value : values) {
                if (!value.getClass().equals(result)) {
                    return Object.class;
                }
            }
            return result;
        }
        @Override public String toString() {
            if (value instanceof int[]) {
                return Arrays.toString((int[]) value);
            } else if (value instanceof float[]) {
                return Arrays.toString((float[]) value);
            } else if (value instanceof double[]) {
                return Arrays.toString((double[]) value);
            } else {
                return Arrays.toString((Object[]) value);
            }
        }
    }

    public static class TLJavaObjectExpression extends TLAtomExpression<Object> {
        public static TLJavaObjectExpression of(Object value) {
            TLJavaObjectExpression jobj = new TLJavaObjectExpression();
            jobj.value = value;
            return jobj;
        }
    }

    public static TLEnvironment defaultEnvironment() {
        TLEnvironment environment = new TLEnvironment();
        environment.put(TLSymbolExpression.of("+"), new TLFunction() {
            @Override
            public TLExpression invoke(TLListExpression args) {
                Integer result = 0;
                for (TLExpression arg : args) {
                    result += (Integer) arg.getValue();
                }
                return expressionOf(result);
            }
        });
        return environment;
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
            TLExpression result = environment.get(symbol);
            if (result == null) {
                throw new RuntimeException("Symbol undefined: " + symbol);
            }
            return result;
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
            } else if (first instanceof TLSymbolExpression && "if".equals(((TLSymbolExpression) first).getValue())) {
                TLExpression condition = expression.get(1);
                TLExpression then = expression.get(2);
                TLExpression els = expression.get(3);
                boolean result = evaluate(condition, environment).asBoolean();
                return evaluate(result ? then : els, environment);
            } else if (first instanceof TLSymbolExpression && "quote".equals(((TLSymbolExpression) first).getValue())) {
                return expression.get(1);
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
            return TLArrayExpression.from(values);
        } else {
            return atomize(token);
        }
    }

    private TLExpression atomize(String token) {
        try {
            return TLNumberExpression.of(Integer.parseInt(token));
        } catch (NumberFormatException ex) {
            // Not an int
        }
        try {
            return TLNumberExpression.of(Double.parseDouble(token));
        } catch (NumberFormatException ex) {
            // Not a double
        }
        return TLSymbolExpression.of(token);
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

    public TLExpression execute(String program, TLEnvironment environment) throws Exception {
        return evaluate(parse(program), environment);
    }
}

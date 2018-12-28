package org.tinylisp.engine;

import java.lang.reflect.Method;
import java.util.*;

public class Engine {

    public static TLAtomExpression<?> expressionOf(Object value) {
        if (value == null) {
            return TLJavaObjectExpression.of(null);
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
        protected List<?> getParameterHelpNames() {
            return Collections.emptyList();
        }
        @Override public Object getValue() {
            return this;
        }
        @Override public boolean asBoolean() {
            return true;
        }
        @Override public String toString() {
            StringBuilder builder = new StringBuilder("TLFunction(");
            for (Object param : getParameterHelpNames()) {
                builder.append(param).append(",");
            }
            if (builder.charAt(builder.length() - 1) == ',') {
                builder.deleteCharAt(builder.length() - 1);
            }
            builder.append(')');
            return builder.toString();
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
        @Override protected List<?> getParameterHelpNames() {
            List<Object> names = new ArrayList<>();
            for (Class<?> param : method.getParameterTypes()) {
                names.add(param.getSimpleName());
            }
            return names;
        }
    }

    public static class TLLambdaFunction extends TLFunction {
        public static TLLambdaFunction of(TLListExpression params, TLListExpression body, TLEnvironment env, Engine engine) {
            TLLambdaFunction lambda = new TLLambdaFunction();
            lambda.params = params;
            lambda.body = body;
            lambda.env = env;
            lambda.engine = engine;
            return lambda;
        }
        private TLListExpression params;
        private TLListExpression body;
        private TLEnvironment env;
        private Engine engine;
        @Override
        public TLExpression invoke(TLListExpression args) throws Exception {
            TLEnvironment tempEnv = new TLEnvironment(env);
            for (int i = 0; i < params.size(); i++) {
                TLSymbolExpression param = (TLSymbolExpression) params.get(i);
                TLExpression arg = args.get(i);
                tempEnv.put(param, arg);
            }
            return engine.evaluate(body, tempEnv);
        }
        @Override protected List<?> getParameterHelpNames() {
            return params;
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
        public Object get(int index) {
            if (value instanceof int[]) {
                return ((int[]) value)[index];
            } else if (value instanceof double[]) {
                return ((double[]) value)[index];
            } else {
                return ((Object[]) value)[index];
            }
        }
        public int length() {
            if (value instanceof int[]) {
                return ((int[]) value).length;
            } else if (value instanceof double[]) {
                return ((double[]) value).length;
            } else {
                return ((Object[]) value).length;
            }
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
                Number result = 0;
                for (TLExpression arg : args) {
                    Object value = arg.getValue();
                    if (result instanceof Double || value instanceof Double) {
                        result = result.doubleValue() + ((Number) value).doubleValue();
                    } else if (value instanceof Integer) {
                        result = result.intValue() + (Integer) value;
                    }
                }
                return expressionOf(result);
            }
        });
        environment.put(TLSymbolExpression.of("-"), new TLFunction() {
            @Override
            public TLExpression invoke(TLListExpression args) {
                Number result = (Number) args.get(0).getValue();
                for (TLExpression arg : args.subList(1, args.size())) {
                    Object value = arg.getValue();
                    if (result instanceof Double || value instanceof Double) {
                        result = result.doubleValue() - ((Number) value).doubleValue();
                    } else if (value instanceof Integer) {
                        result = result.intValue() - (Integer) value;
                    }
                }
                return expressionOf(result);
            }
        });
        environment.put(TLSymbolExpression.of("*"), new TLFunction() {
            @Override
            public TLExpression invoke(TLListExpression args) {
                Number result = 1;
                for (TLExpression arg : args) {
                    Object value = arg.getValue();
                    if (result instanceof Double || value instanceof Double) {
                        result = result.doubleValue() * ((Number) value).doubleValue();
                    } else if (value instanceof Integer) {
                        result = result.intValue() * (Integer) value;
                    }
                }
                return expressionOf(result);
            }
        });
        environment.put(TLSymbolExpression.of("/"), new TLFunction() {
            @Override
            public TLExpression invoke(TLListExpression args) {
                Number result = (Number) args.get(0).getValue();
                for (TLExpression arg : args.subList(1, args.size())) {
                    Object value = arg.getValue();
                    if (result instanceof Double || value instanceof Double) {
                        result = result.doubleValue() / ((Number) value).doubleValue();
                    } else if (value instanceof Integer) {
                        result = result.intValue() / (Integer) value;
                    }
                }
                return expressionOf(result);
            }
        });
        environment.put(TLSymbolExpression.of("<"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                double first = ((Number) args.get(0).getValue()).doubleValue();
                for (TLExpression arg : args.subList(1, args.size())) {
                    if (first >= ((Number) arg.getValue()).doubleValue()) {
                        return expressionOf(false);
                    }
                }
                return expressionOf(true);
            }
        });
        environment.put(TLSymbolExpression.of(">"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                double first = ((Number) args.get(0).getValue()).doubleValue();
                for (TLExpression arg : args.subList(1, args.size())) {
                    if (first <= ((Number) arg.getValue()).doubleValue()) {
                        return expressionOf(false);
                    }
                }
                return expressionOf(true);
            }
        });
        environment.put(TLSymbolExpression.of("<="), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                double first = ((Number) args.get(0).getValue()).doubleValue();
                for (TLExpression arg : args.subList(1, args.size())) {
                    if (first > ((Number) arg.getValue()).doubleValue()) {
                        return expressionOf(false);
                    }
                }
                return expressionOf(true);
            }
        });
        environment.put(TLSymbolExpression.of(">="), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                double first = ((Number) args.get(0).getValue()).doubleValue();
                for (TLExpression arg : args.subList(1, args.size())) {
                    if (first < ((Number) arg.getValue()).doubleValue()) {
                        return expressionOf(false);
                    }
                }
                return expressionOf(true);
            }
        });
        environment.put(TLSymbolExpression.of("is"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                Object arg1 = args.get(0).getValue();
                Object arg2 = args.get(1).getValue();
                return expressionOf(arg1 == arg2);
            }
        });
        environment.put(TLSymbolExpression.of("eq"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                Object arg1 = args.get(0).getValue();
                Object arg2 = args.get(1).getValue();
                return expressionOf(arg1 == arg2 || arg1 != null && arg1.equals(arg2));
            }
        });
        environment.alias(TLSymbolExpression.of("eq"), TLSymbolExpression.of("="));
        environment.put(TLSymbolExpression.of("car"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                TLListExpression arg = (TLListExpression) args.get(0);
                return arg.get(0);
            }
        });
        environment.put(TLSymbolExpression.of("cdr"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                TLListExpression arg = (TLListExpression) args.get(0);
                return new TLListExpression(arg.subList(1, arg.size()));
            }
        });
        environment.put(TLSymbolExpression.of("cons"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                TLListExpression result = new TLListExpression();
                result.add(args.get(0));
                TLExpression rest = args.get(1);
                if (rest instanceof TLListExpression) {
                    result.addAll((TLListExpression) rest);
                } else {
                    result.add(rest);
                }
                return result;
            }
        });
        environment.put(TLSymbolExpression.of("length"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                TLExpression listOrArray = args.get(0);
                if (listOrArray instanceof TLArrayExpression) {
                    return expressionOf(((TLArrayExpression) listOrArray).length());
                } else {
                    return expressionOf(((TLListExpression) listOrArray).size());
                }
            }
        });
        environment.put(TLSymbolExpression.of("list"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                return args;
            }
        });
        environment.put(TLSymbolExpression.of("map"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) throws Exception {
                TLListExpression result = new TLListExpression();
                TLFunction function = (TLFunction) args.get(0);
                TLListExpression list = (TLListExpression) args.get(1);
                for (TLExpression arg : list) {
                    result.add(function.invoke(new TLListExpression(Collections.singletonList(arg))));
                }
                return result;
            }
        });
        environment.put(TLSymbolExpression.of("null"), expressionOf(null));
        environment.put(TLSymbolExpression.of("nth"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) {
                int n = (Integer) args.get(0).getValue();
                TLExpression listOrArray = args.get(1);
                if (listOrArray instanceof TLArrayExpression) {
                    return expressionOf(((TLArrayExpression) listOrArray).get(n));
                } else {
                    return ((TLListExpression) listOrArray).get(n);
                }
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
        public TLExpression alias(TLSymbolExpression from, TLSymbolExpression to) {
            return put(to, get(from));
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
            if (expression.isEmpty()) {
                // Empty list is nil/false
                return expression;
            }
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
                try {
                    return apply(function, args);
                } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
                    throw new TLRuntimeException(first + ": " + function + "\n" + ex, ex);
                }
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
        } else if ("\"".equals(token)) {
            String string = tokens.remove(0);
            tokens.remove(0);
            return TLJavaObjectExpression.of(string);
        } else {
            return atomize(token);
        }
    }

    private TLExpression atomize(String token) {
        try {
            return TLJavaObjectExpression.of(Integer.parseInt(token));
        } catch (NumberFormatException ex) {
            // Not an int
        }
        try {
            return TLJavaObjectExpression.of(Double.parseDouble(token));
        } catch (NumberFormatException ex) {
            // Not a double
        }
        return TLSymbolExpression.of(token);
    }

    public ArrayList<String> tokenize(String input) {
        ArrayList<String> tokens = new ArrayList<>();
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inString) {
                if (c == '"') {
                    inString = false;
                    tokens.add(input.substring(start, i));
                    start = i + 1;
                    tokens.add(String.valueOf(c));
                }
            } else {
                if (c == '(' || c == ')' || c == '[' || c == ']') {
                    addIfNotEmpty(tokens, input.substring(start, i));
                    start = i + 1;
                    tokens.add(String.valueOf(c));
                } else if (Character.isWhitespace(c)) {
                    addIfNotEmpty(tokens, input.substring(start, i));
                    start = i + 1;
                } else if (c == '"') {
                    inString = true;
                    tokens.add(String.valueOf(c));
                    start = i + 1;
                }
            }
        }
        addIfNotEmpty(tokens, input.substring(start));
        return tokens;
    }

    private void addIfNotEmpty(List<String> list, String value) {
        if (!value.trim().isEmpty()) {
            list.add(value);
        }
    }

    public TLExpression execute(String program, TLEnvironment environment) throws Exception {
        return evaluate(parse(program), environment);
    }

    public static class TLRuntimeException extends RuntimeException {
        public TLRuntimeException() {
        }
        public TLRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

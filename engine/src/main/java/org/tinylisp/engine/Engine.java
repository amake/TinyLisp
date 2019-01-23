package org.tinylisp.engine;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class Engine {

    public static final String VERSION = "@version@";

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
            return listToString("TLFunction(", getParameterHelpNames(), ",", ")");
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
            return params.getValue();
        }
    }

    public static class TLListExpression extends ArrayList<TLExpression> implements TLExpression {
        public static TLListExpression of (Collection<?> items) {
            TLListExpression list = new TLListExpression();
            for (Object item : items) {
                list.add(expressionOf(item));
            }
            return list;
        }
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
        @Override public String toString() {
            return listToString("(", this, " ", ")");
        }
    }

    public abstract static class TLAtomExpression<T> implements TLExpression {
        protected T value;
        public T getValue() {
            return value;
        }
        @Override public String toString() {
            return value instanceof String ? "\"" + escapeString((String) value) + '"' : String.valueOf(value);
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
        @Override public String toString() {
            return value;
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
            StringBuilder builder = new StringBuilder("[");
            if (value instanceof int[]) {
                for (int i : ((int[]) value)) {
                    builder.append(i).append(' ');
                }
            } else if (value instanceof double[]) {
                for (double d : ((double[]) value)) {
                    builder.append(d).append(' ');
                }
            } else {
                for (Object o : ((Object[]) value)) {
                    builder.append(o).append(' ');
                }
            }
            if (builder.charAt(builder.length() - 1) == ' ') {
                builder.deleteCharAt(builder.length() - 1);
            }
            builder.append(']');
            return builder.toString();
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
        final TLEnvironment environment = new TLEnvironment();
        final Engine engine = new Engine();
        environment.put(TLSymbolExpression.of("+"), new TLFunction() {
            @Override
            public TLExpression invoke(TLListExpression args) {
                BigDecimal result = BigDecimal.ZERO;
                for (TLExpression arg : args) {
                    Number value = (Number) arg.getValue();
                    result = result.add(toBigDecimal(value));
                }
                return expressionOf(reduceBigDecimal(result));
            }
        });
        environment.put(TLSymbolExpression.of("-"), new TLFunction() {
            @Override
            public TLExpression invoke(TLListExpression args) {
                BigDecimal result = toBigDecimal((Number) args.get(0).getValue());
                for (TLExpression arg : args.subList(1, args.size())) {
                    Number value = (Number) arg.getValue();
                    result = result.subtract(toBigDecimal(value));
                }
                return expressionOf(reduceBigDecimal(result));
            }
        });
        environment.put(TLSymbolExpression.of("*"), new TLFunction() {
            @Override
            public TLExpression invoke(TLListExpression args) {
                BigDecimal result = BigDecimal.ONE;
                for (TLExpression arg : args) {
                    Number value = (Number) arg.getValue();
                    result = result.multiply(toBigDecimal(value));
                }
                return expressionOf(reduceBigDecimal(result));
            }
        });
        environment.put(TLSymbolExpression.of("/"), new TLFunction() {
            @Override
            public TLExpression invoke(TLListExpression args) {
                BigDecimal result = toBigDecimal((Number) args.get(0).getValue());
                for (TLExpression arg : args.subList(1, args.size())) {
                    Number value = (Number) arg.getValue();
                    result = result.divide(toBigDecimal(value), 16, RoundingMode.UP);
                }
                return expressionOf(reduceBigDecimal(result));
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
        environment.put(TLSymbolExpression.of("eval"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) throws Exception {
                return engine.evaluate(args.get(0), environment);
            }
        });
        environment.put(TLSymbolExpression.of("parse"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) throws Exception {
                return engine.parse((String) args.get(0).getValue());
            }
        });
        environment.put(TLSymbolExpression.of("apply"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) throws Exception {
                TLListExpression applyArgs = new TLListExpression(args.subList(1, args.size()));
                TLExpression last = applyArgs.get(applyArgs.size() - 1);
                if (last instanceof TLListExpression) {
                    applyArgs.remove(applyArgs.size() - 1);
                    applyArgs.addAll((TLListExpression) last);
                }
                return engine.apply((TLFunction) args.get(0), applyArgs);
            }
        });
        environment.put(TLSymbolExpression.of("exec"), new TLFunction() {
            @Override public TLExpression invoke(TLListExpression args) throws Exception {
                return engine.execute((String) args.get(0).getValue(), environment);
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
        public List<String> complete(String prefix) {
            List<String> result = new ArrayList<>();
            for (Engine.TLSymbolExpression symbol : keySet()) {
                String name = symbol.getValue();
                if (name.startsWith(prefix)) {
                    result.add(name);
                }
            }
            Collections.sort(result);
            return Collections.unmodifiableList(result);
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
            if (isSymbol(first, "def")) {
                TLSymbolExpression name = (TLSymbolExpression) expression.get(1);
                TLExpression value = expression.get(2);
                TLExpression eValue = evaluate(value, environment);
                environment.put(name, eValue);
                return eValue;
            } else if (isSymbol(first, "lambda")) {
                TLListExpression params = (TLListExpression) expression.get(1);
                TLListExpression body = (TLListExpression) expression.get(2);
                return TLLambdaFunction.of(params, body, environment, this);
            } else if (isSymbol(first, "if")) {
                TLExpression condition = expression.get(1);
                TLExpression then = expression.get(2);
                TLListExpression els = new TLListExpression(expression.subList(3, expression.size()));
                els.add(0, TLSymbolExpression.of("progn"));
                boolean result = evaluate(condition, environment).asBoolean();
                return evaluate(result ? then : els, environment);
            } else if (isSymbol(first, "quote")) {
                return expression.get(1);
            } else if (isSymbol(first, "progn")) {
                TLExpression result = expressionOf(null);
                for (TLExpression exp : expression.subList(1, expression.size())) {
                    result = evaluate(exp, environment);
                }
                return result;
            } else if (isSymbol(first, "let*")) {
                TLListExpression defs = (TLListExpression) expression.get(1);
                TLListExpression body = new TLListExpression(expression.subList(2, expression.size()));
                body.add(0, TLSymbolExpression.of("progn"));
                TLEnvironment localEnvironment = new TLEnvironment(environment);
                for (TLExpression exp : defs) {
                    TLListExpression def = (TLListExpression) exp;
                    TLSymbolExpression symbol = (TLSymbolExpression) def.get(0);
                    localEnvironment.put(symbol, evaluate(def.get(1), localEnvironment));
                }
                return evaluate(body, localEnvironment);
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
        ArrayList<String> tokens = tokenize(input);
        TLExpression expression = readTokens(tokens);
        if (tokens.isEmpty()) {
            return expression;
        } else {
            TLListExpression result = new TLListExpression();
            result.add(TLSymbolExpression.of("progn"));
            result.add(expression);
            while (!tokens.isEmpty()) {
                result.add(readTokens(tokens));
            }
            return result;
        }
    }

    private TLExpression readTokens(ArrayList<String> tokens) {
        String token;
        do {
            if (tokens.isEmpty()) {
                throw new IllegalArgumentException("End of token list");
            }
            token = tokens.remove(0);
        } while (token.trim().isEmpty());
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
        } else if ("'".equals(token)) {
            TLListExpression expression = new TLListExpression();
            expression.add(atomize("quote"));
            expression.add(readTokens(tokens));
            return expression;
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
        if ("null".equals(token)) {
            return TLJavaObjectExpression.of(null);
        } else if ("true".equals(token)) {
            return TLJavaObjectExpression.of(true);
        } else if ("false".equals(token)) {
            return TLJavaObjectExpression.of(false);
        } else {
            return TLSymbolExpression.of(token);
        }
    }

    public ArrayList<String> tokenize(String input) {
        ArrayList<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inString) {
                if (c == '"') {
                    inString = false;
                    tokens.add(token.toString());
                    token = new StringBuilder();
                    tokens.add(String.valueOf(c));
                } else if (c == '\\') {
                    token.append(input.charAt(++i));
                } else {
                    token.append(c);
                }
            } else {
                if (isBreakingChar(c)) {
                    if (token.length() > 0) {
                        tokens.add(token.toString());
                        token = new StringBuilder();
                    }
                    tokens.add(String.valueOf(c));
                    inString = c == '"';
                } else {
                    token.append(c);
                }
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }
        return tokens;
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

    /* Utility functions */

    private boolean isBreakingChar(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '\'' || c == '"' || Character.isWhitespace(c);
    }

    private static String escapeString(String str) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' || c == '"') {
                result.append('\\');
            }
            result.append(c);
        }
        return result.toString();
    }

    private static boolean isSymbol(Object obj, String name) {
        if (obj instanceof TLSymbolExpression) {
            TLSymbolExpression symbol = (TLSymbolExpression) obj;
            return name.equals(symbol.getValue());
        } else {
            return false;
        }
    }

    private static String listToString(String prefix, Iterable<?> items, String delimiter, String suffix) {
        StringBuilder builder = new StringBuilder(prefix);
        for (Object item : items) {
            builder.append(item).append(delimiter);
        }
        if (builder.substring(builder.length() - delimiter.length()).equals(delimiter)) {
            builder.delete(builder.length() - delimiter.length(), builder.length());
        }
        builder.append(suffix);
        return builder.toString();
    }

    private static BigDecimal toBigDecimal(Number value) {
        if (value instanceof Double) {
            return BigDecimal.valueOf(value.doubleValue());
        } else if (value instanceof Integer) {
            return BigDecimal.valueOf(value.intValue());
        } else if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        } else {
            throw new IllegalArgumentException("Unsupported number type: " + value.getClass());
        }
    }

    static Number reduceBigDecimal(BigDecimal value) {
        if (value.signum() == 0 || value.scale() <= 0 || value.stripTrailingZeros().scale() <= 0) {
            try {
                return value.intValueExact();
            } catch (ArithmeticException ex) {
                // Doesn't fit in an int, but is an integer
                return value.setScale(0, RoundingMode.UNNECESSARY);
            }
        } else {
            double dbl = value.doubleValue();
            if (!Double.isInfinite(dbl)) {
                return dbl;
            } else {
                return value.stripTrailingZeros();
            }
        }
    }
}

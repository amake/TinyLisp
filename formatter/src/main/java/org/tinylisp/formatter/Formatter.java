package org.tinylisp.formatter;

import org.tinylisp.engine.Engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Formatter {

    private static final Logger LOGGER = Logger.getLogger(Formatter.class.getName());

    private final Engine mEngine = new Engine();
    private List<Visitor> mVisitors = new ArrayList<>(Arrays.asList(WHITESPACE_NORMALIZER, LET_FORMATTER, IF_FORMATTER,
            PROGN_FORMATTER));

    public String format(String program) {
        TLToken token;
        try {
            token = parse(program);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Failed to format input", ex);
            return program;
        }
        for (Visitor visitor : mVisitors) {
            walkTree(null, token, 0, visitor);
        }
        return token.toString();
    }

    public void addVisitor(Visitor visitor) {
        mVisitors.add(visitor);
    }

    private static final Visitor WHITESPACE_NORMALIZER = new Visitor() {
        @Override
        public void visit(TLAggregateToken parent, TLToken child, int depth) {
            if (isWhitespace(child)) {
                ((TLAtomToken) child).value = " ";
            } else if (child instanceof TLAggregateToken) {
                TLAggregateToken aggregate = (TLAggregateToken) child;
                removeConsecutiveWhitespace(aggregate);
                removeHeadWhitespace(aggregate);
            }
        }
        private void removeConsecutiveWhitespace(TLAggregateToken aggregate) {
            for (int i = 0; i < aggregate.size(); i++) {
                if (isWhitespace(aggregate.get(i))) {
                    for (int j = i + 1; j < aggregate.size(); j++) {
                        if (isWhitespace(aggregate.get(j))) {
                            aggregate.remove(j--);
                        } else {
                            i = j;
                            break;
                        }
                    }
                }
            }
        }
        private void removeHeadWhitespace(TLAggregateToken aggregate) {
            if (isList(aggregate) || isArray(aggregate) || isQuoted(aggregate)) {
                if (isWhitespace(aggregate.get(1))) {
                    aggregate.remove(1);
                }
            }
        }
    };

    private static final Visitor LET_FORMATTER = new Visitor() {
        @Override public void visit(TLAggregateToken parent, TLToken child, int depth) {
            if (isLet(child)) {
                formatLet((TLAggregateToken) child);
            }
        }
        private boolean isLet(TLToken token) {
            return isFunctionCall(token, "let*") || isFunctionCall(token, "let");
        }
        private void formatLet(TLAggregateToken let) {
            if (countNonWhitespace(let) > 3) {
                int paramsIdx = skipWhitespace(let, 2);
                TLToken params = let.get(paramsIdx);
                if (isList(params)) {
                    formatParams((TLAggregateToken) params);
                }
            }
            linebreakAfterRest(let, 2);
        }
        private void formatParams(TLAggregateToken params) {
            linebreakAfterRest(params, 1);
        }
    };

    private static final Visitor IF_FORMATTER = new Visitor() {
        @Override public void visit(TLAggregateToken parent, TLToken child, int depth) {
            if (isFunctionCall(child, "if")) {
                formatIf((TLAggregateToken) child);
            }
        }
        private void formatIf(TLAggregateToken ifExpr) {
            linebreakAfterRest(ifExpr, 2);
            // Indent consequent if present
            if (countNonWhitespace(ifExpr) > 4) {
                int consequentIdx = indexOfNthNonWhitespace(ifExpr, 3);
                ifExpr.add(consequentIdx, new TLAtomToken(" "));
            }
        }
    };

    private static final Visitor PROGN_FORMATTER = new Visitor() {
        @Override public void visit(TLAggregateToken parent, TLToken child, int depth) {
            if (isFunctionCall(child, "progn")) {
                linebreakAfterRest(((TLAggregateToken) child), 1);
            }
        }
    };

    private static void linebreakAfterRest(TLAggregateToken aggregate, int from) {
        for (int i = skipWhitespace(aggregate, from); i < aggregate.size() - 2; i += 2) {
            linebreakAt(aggregate, i + 1);
        }
    }

    private static void linebreakAt(TLAggregateToken aggregate, int idx) {
        TLToken linebreak = new TLAtomToken("\n");
        if (idx < aggregate.size() && isWhitespace(aggregate.get(idx))) {
            aggregate.set(idx, linebreak);
        } else {
            aggregate.add(idx, linebreak);
        }
    }

    private static int indexOfNthNonWhitespace(TLAggregateToken aggregate, int n) {
        for (int i = 0; i < aggregate.size(); i++) {
            if (!isWhitespace(aggregate.get(i)) && n-- == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int countNonWhitespace(TLAggregateToken aggregate) {
        return countNonWhitespace(aggregate, 0);
    }

    private static int countNonWhitespace(TLAggregateToken aggregate, int from) {
        int count = 0;
        for (int i = from; i < aggregate.size(); i++) {
            if (!isWhitespace(aggregate.get(i))) {
                count++;
            }
        }
        return count;
    }

    private static int skipWhitespace(TLAggregateToken aggregate, int idx) {
        for (; idx < aggregate.size(); idx++) {
            if (!isWhitespace(aggregate.get(idx))) {
                return idx;
            }
        }
        return idx;
    }

    private static boolean isFunctionCall(TLToken token, String functionName) {
        if (!(token instanceof TLAggregateToken)) {
            return false;
        }
        TLAggregateToken aggregate = (TLAggregateToken) token;
        if (aggregate.size() < 3) {
            return false;
        }
        TLToken second = aggregate.get(1);
        return isList(aggregate) && isAtom(second, functionName);
    }

    private static boolean isAtom(TLToken token, String value) {
        return token instanceof TLAtomToken && value.equals(((TLAtomToken) token).value);
    }

    private static boolean isWhitespace(TLToken token) {
        return token instanceof TLAtomToken && isWhitespace(((TLAtomToken) token).value);
    }

    private static boolean isWhitespace(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isList(TLToken token) {
        return token instanceof TLAggregateToken && isDelimitedBy((TLAggregateToken) token, "(", ")");
    }

    private static boolean isArray(TLToken token) {
        return token instanceof TLAggregateToken && isDelimitedBy((TLAggregateToken) token, "[", "]");
    }

    private static boolean isString(TLToken token) {
        return token instanceof TLAggregateToken && isDelimitedBy((TLAggregateToken) token, "\"", "\"");
    }

    private static boolean isComment(TLToken token) {
        return token instanceof TLAggregateToken && isAtom(((TLAggregateToken) token).get(0), ";");
    }

    private static boolean isQuoted(TLToken token) {
        return token instanceof TLAggregateToken && isAtom(((TLAggregateToken) token).get(0), "'");
    }

    private static boolean isNewline(TLToken token) {
        return token instanceof TLAtomToken && ((TLAtomToken) token).value.equals("\n");
    }

    private static boolean isDelimitedBy(TLAggregateToken aggregate, String start, String end) {
        return aggregate.size() > 1 && isAtom(aggregate.get(0), start) && isAtom(aggregate.get(aggregate.size() - 1), end);
    }

    public interface Visitor {
        void visit(TLAggregateToken parent, TLToken child, int depth);
    }

    private void walkTree(TLAggregateToken parent, TLToken child, int depth, Visitor visitor) {
        visitor.visit(parent, child, depth);
        if (child instanceof TLAggregateToken) {
            TLAggregateToken aggregate = (TLAggregateToken) child;
            for (int i = 0; i < aggregate.size(); i++) {
                walkTree(aggregate, aggregate.get(i), depth + 1, visitor);
            }
        }
    }

    private TLToken parse(String input) {
        ArrayList<String> tokens = mEngine.tokenize(input);
        return readTokens(tokens);
    }

    public interface TLToken {
        public void append(StringBuilder builder);
    }

    public static class TLAtomToken implements TLToken {
        public String value;
        TLAtomToken(String value) {
            this.value = value;
        }
        @Override public String toString() {
            return value;
        }
        @Override public void append(StringBuilder builder) {
            builder.append(value);
        }
    }

    public static class TLAggregateToken extends ArrayList<TLToken> implements TLToken {
        @Override public String toString() {
            StringBuilder builder = new StringBuilder();
            append(builder);
            return builder.toString();
        }
        @Override public void append(StringBuilder builder) {
            int lastNewLine = builder.lastIndexOf("\n");
            int lineStart = lastNewLine == -1 ? 0 : lastNewLine;
            int indent = builder.length() - lineStart + 1;
            for (TLToken token : this) {
                token.append(builder);
                if (isNewline(token)) {
                    for (int i = 0; i < indent; i++) {
                        builder.append(' ');
                    }
                }
            }
        }
    }

    private TLToken readTokens(ArrayList<String> tokens) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("End of token list");
        }
        String token = tokens.remove(0);
        if ("(".equals(token) || "[".equals(token) || "\"".equals(token)) {
            TLAggregateToken expression = new TLAggregateToken();
            expression.add(new TLAtomToken(token));
            String end = "(".equals(token) ? ")" : "[".equals(token) ? "]" : "\"";
            while (!end.equals(tokens.get(0))) {
                expression.add(readTokens(tokens));
            }
            expression.add(new TLAtomToken(tokens.remove(0)));
            return expression;
        } else if (";".equals(token)) {
            TLAggregateToken expression = new TLAggregateToken();
            expression.add(new TLAtomToken(token));
            while (!tokens.isEmpty() && !"\n".equals(tokens.get(0))) {
                expression.add(readTokens(tokens));
            }
            if (!tokens.isEmpty()) {
                expression.add(new TLAtomToken(tokens.remove(0)));
            }
            return expression;
        } else if ("'".equals(token)) {
            TLAggregateToken expression = new TLAggregateToken();
            expression.add(new TLAtomToken(token));
            while (isWhitespace(tokens.get(0))) {
                expression.add(new TLAtomToken(tokens.remove(0)));
            }
            expression.add(readTokens(tokens));
            return expression;
        } else {
            return new TLAtomToken(token);
        }
    }
}

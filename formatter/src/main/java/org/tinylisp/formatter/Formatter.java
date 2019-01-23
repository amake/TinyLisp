package org.tinylisp.formatter;

import org.tinylisp.engine.Engine;

import java.util.ArrayList;

public class Formatter {

    private final Engine mEngine = new Engine();

    public String format(String program) {
        TLToken token = parse(program);
        walkTree(token, new Visitor() {
            @Override
            public void visit(TLToken token) {
                if (token instanceof TLAtomToken) {
                    ((TLAtomToken) token).value += '!';
                }
            }
        });
        return token.toString();
    }

    private interface Visitor {
        void visit(TLToken token);
    }

    private void walkTree(TLToken token, Visitor visitor) {
        visitor.visit(token);
        if (token instanceof TLAggregateToken) {
            TLAggregateToken aggregate = (TLAggregateToken) token;
            for (TLToken child : aggregate) {
                visitor.visit(child);
            }
        }
    }

    private TLToken parse(String input) {
        ArrayList<String> tokens = mEngine.tokenize(input);
        return readTokens(tokens);
    }

    private interface TLToken {
    }

    private static class TLAtomToken implements TLToken {
        private String value;
        TLAtomToken(String value) {
            this.value = value;
        }
        @Override public String toString() {
            return value;
        }
    }

    private static class TLAggregateToken extends ArrayList<TLToken> implements TLToken {
        @Override public String toString() {
            StringBuilder builder = new StringBuilder();
            for (TLToken token : this) {
                builder.append(token.toString());
            }
            return builder.toString();
        }
    }

    private TLToken readTokens(ArrayList<String> tokens) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("End of token list");
        }
        String token = tokens.remove(0);
        if ("(".equals(token) || "[".equals(token)) {
            TLAggregateToken expression = new TLAggregateToken();
            expression.add(new TLAtomToken(token));
            String end = "(".equals(token) ? ")" : "]";
            while (!end.equals(tokens.get(0))) {
                expression.add(readTokens(tokens));
            }
            expression.add(new TLAtomToken(tokens.remove(0)));
            return expression;
        } else {
            return new TLAtomToken(token);
        }
    }
}

package org.tinylisp.engine;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class EngineTest {

    private Engine engine;
    private Engine.TLEnvironment env;

    @Before
    public void setUp() {
        engine = new Engine();
        env = new Engine.TLEnvironment();
        env.put(Engine.TLSymbolExpression.of("add"), new Engine.TLFunction() {
            @Override public Engine.TLExpression invoke(Engine.TLListExpression args) {
                int result = 0;
                for (Engine.TLExpression arg : args) {
                    result += (Integer) arg.getValue();
                }
                return Engine.expressionOf(result);
            }
        });
        env.put(Engine.TLSymbolExpression.of("addArray"), new Engine.TLFunction() {
            @Override public Engine.TLExpression invoke(Engine.TLListExpression args) {
                int result = 0;
                for (Engine.TLExpression arg : args) {
                    Object array = arg.getValue();
                    for (int i = 0; i < Array.getLength(array); i++) {
                        result += Array.getInt(array, i);
                    }
                }
                return Engine.expressionOf(result);
            }
        });
        env.put(Engine.TLSymbolExpression.of("<"), new Engine.TLFunction() {
            @Override public Engine.TLExpression invoke(Engine.TLListExpression args) {
                int first = (Integer) args.get(0).getValue();
                for (int i = 1; i < args.size(); i++) {
                    int arg = (Integer) args.get(i).getValue();
                    if (first > arg) {
                        return Engine.expressionOf(false);
                    }
                }
                return Engine.expressionOf(true);
            }
        });
        env.put(Engine.TLSymbolExpression.of("concat"), new Engine.TLFunction() {
            @Override public Engine.TLExpression invoke(Engine.TLListExpression args) {
                StringBuilder builder = new StringBuilder();
                for (Engine.TLExpression arg : args) {
                    builder.append((String) arg.getValue());
                }
                return Engine.expressionOf(builder.toString());
            }
        });
    }

    @Test
    public void testEvaluate() throws Exception {
        Engine.TLListExpression exp = new Engine.TLListExpression();
        exp.add(Engine.TLSymbolExpression.of("add"));
        exp.add(Engine.TLNumberExpression.of(1));
        exp.add(Engine.TLNumberExpression.of(2));
        exp.add(Engine.TLNumberExpression.of(3));

        assertEquals(Engine.expressionOf(6), engine.evaluate(exp, env));
    }

    @Test
    public void testExecute() throws Exception {
        assertEquals(10, engine.execute("(add 1 2 3 4)", env).getValue());
    }

    @Test
    public void testArrays() throws Exception {
        assertEquals(15, engine.execute("(addArray [1 2 3 4 5])", env).getValue());
        assertEquals(30, engine.execute("(addArray [1 2 3 4 5] [1 2 3 4 5])", env).getValue());
        {
            Object result = engine.execute("[1 2 3 4 5]", env).getValue();
            assertTrue(result instanceof int[]);
            assertArrayEquals((int[]) result, new int[] {1, 2, 3, 4, 5});
        }
        {
            Object result = engine.execute("[1.1 2.2 3.3 4.4 5.5]", env).getValue();
            assertTrue(result instanceof double[]);
            assertArrayEquals(new double[] {1.1, 2.2, 3.3, 4.4, 5.5}, (double[]) result, 0);
        }
    }

    @Test
    public void testMethodFunction() throws Exception {
        Method method = Integer.class.getMethod("toString", int.class, int.class);
        Engine.TLMethodFunction split = Engine.TLMethodFunction.of(null, method);
        env.put(Engine.TLSymbolExpression.of("toString"), split);
        assertEquals("b", engine.execute("(toString 11 16)", env).getValue());
    }

    @Test
    public void testSet() throws Exception {
        Engine.TLExpression result = engine.execute("(set foo 123)", env);
        assertNull("Result of `set' is null", result.getValue());
        assertTrue(env.containsKey(Engine.TLSymbolExpression.of("foo")));
        assertEquals(Engine.expressionOf(123), env.get(Engine.TLSymbolExpression.of("foo")));
    }

    @Test
    public void testLambda() throws Exception {
        assertEquals(2, engine.execute("((lambda (x) (add 1 x)) 1)", env).getValue());
        engine.execute("(set increment (lambda (x) (add 1 x)))", env);
        assertEquals(5, engine.execute("(increment 4)", env).getValue());
    }

    @Test
    public void testIf() throws Exception {
        assertEquals(1, engine.execute("(if (< 1 2) 1 2)", env).getValue());
        assertEquals(2, engine.execute("(if (< 1 2 0) 1 2)", env).getValue());
    }

    @Test
    public void testQuote() throws Exception {
        assertEquals("foo", engine.execute("(quote foo)", env).getValue());
        assertEquals(Arrays.asList("foo", "bar"), engine.execute("(quote (foo bar))", env).getValue());
        assertEquals(Arrays.asList("foo", "bar", Arrays.asList("baz", "buzz")),
                engine.execute("(quote (foo bar (baz buzz)))", env).getValue());
    }

    @Test
    public void testTokenize() {
        assertEquals(Collections.singletonList("foo"), engine.tokenize("foo"));
        assertEquals(Arrays.asList("(", "a", "b", "c", ")"), engine.tokenize("(a b c)"));
        assertEquals(Arrays.asList("(", "a", "b", "c", ")"), engine.tokenize("( a b c ) "));
        assertEquals(Arrays.asList("(", "a", "b", "[", "c", "]", ")"), engine.tokenize("(a b [c])"));
        assertEquals(Arrays.asList("(", "a", "b", "\"", "foo bar", "\"", ")"),
                engine.tokenize("(a b \"foo bar\")"));
        assertEquals(Arrays.asList("(", "a", "b", "\"", "  ", "\"", ")"),
                engine.tokenize("(a b \"  \")"));
    }

    @Test
    public void testString() throws Exception {
        assertTrue("String should parse to Java object, not symbol",
                engine.execute("\"foo\"", env) instanceof Engine.TLJavaObjectExpression);
        assertEquals("foo", engine.execute("\"foo\"", env).getValue());
        assertEquals(" ", engine.execute("\" \"", env).getValue());
        try {
            engine.execute("foo", env);
            fail("String without quotes is a symbol; evaluating an undefined symbol is an error");
        } catch (Exception ex) {
            // Should fail
        }
        assertEquals("foobar baz",
                engine.execute("(concat \"foo\" \"bar baz\")", env).getValue());
        assertTrue("String return value should wrap to Java object, not symbol",
                engine.execute("(concat \"foo\" \"bar baz\")", env) instanceof Engine.TLJavaObjectExpression);
    }

    @Test
    public void testFunctionString() throws Exception {
        assertEquals("TLFunction()", engine.execute("(lambda () ())", env).toString());
        assertEquals("TLFunction(x,y,z)", engine.execute("(lambda (x y z) ())", env).toString());
        Method method = Integer.class.getMethod("toString", int.class, int.class);
        Engine.TLMethodFunction toString = Engine.TLMethodFunction.of(null, method);
        env.put(Engine.TLSymbolExpression.of("toString"), toString);
        assertEquals("TLFunction(int,int)", toString.toString());
        assertEquals("TLFunction(int,int)", engine.execute("toString", env).toString());
    }
}

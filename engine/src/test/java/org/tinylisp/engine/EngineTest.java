package org.tinylisp.engine;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class EngineTest {

    private static double DELTA = 0.0000000000000001;

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
        exp.add(Engine.expressionOf(1));
        exp.add(Engine.expressionOf(2));
        exp.add(Engine.expressionOf(3));

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
    public void testDef() throws Exception {
        assertEquals("Result of `def' is the value",
                123, engine.execute("(def foo 123)", env).getValue());
        assertTrue(env.containsKey(Engine.TLSymbolExpression.of("foo")));
        assertEquals(Engine.expressionOf(123), env.get(Engine.TLSymbolExpression.of("foo")));
    }

    @Test
    public void testLambda() throws Exception {
        assertEquals(2, engine.execute("((lambda (x) (add 1 x)) 1)", env).getValue());
        engine.execute("(def increment (lambda (x) (add 1 x)))", env);
        assertEquals(5, engine.execute("(increment 4)", env).getValue());
    }

    @Test
    public void testIf() throws Exception {
        assertEquals(1, engine.execute("(if (< 1 2) 1 2)", env).getValue());
        assertEquals(2, engine.execute("(if (< 1 2 0) 1 2)", env).getValue());
        assertEquals("implied progn around else",
                3, engine.execute("(if (< 1 2 0) 1 2 3)", env).getValue());
    }

    @Test
    public void testRecursiveReference() throws Exception {
        Engine.TLEnvironment stdEnv = Engine.defaultEnvironment();
        engine.execute("(def ** (lambda (x y) (if (<= y 0) 1 (* x (** x (- y 1))))))", stdEnv);
        assertEquals(256, engine.execute("(** 2 8)", stdEnv).getValue());
    }

    @Test
    public void testProgn() throws Exception {
        assertEquals(6, engine.execute("(def x (progn (add 2 8) (add 1 5)))", env).getValue());
        assertEquals(6, engine.execute("x", env).getValue());
        assertEquals("Implied progn around top-level sexps",
                6, engine.execute("(add 2 8) (add 1 5)", env).getValue());
    }

    @Test
    public void testQuote() throws Exception {
        assertEquals("foo", engine.execute("(quote foo)", env).getValue());
        assertEquals(Arrays.asList("foo", "bar"), engine.execute("(quote (foo bar))", env).getValue());
        assertEquals(Arrays.asList("foo", "bar", Arrays.asList("baz", "buzz")),
                engine.execute("(quote (foo bar (baz buzz)))", env).getValue());
    }

    @Test
    public void testQuoteSugar() throws Exception {
        assertEquals("foo", engine.execute("'foo", env).getValue());
        assertEquals(Arrays.asList("quote", "foo"), engine.parse("'foo").getValue());
        assertEquals(Arrays.asList(1, 2, 3), engine.execute("'(1 2 3)", env).getValue());
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
        assertEquals(Arrays.asList("'", "(", "a", "b", "c", ")"), engine.tokenize("'(a b c)"));
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

    @Test
    public void testListString() throws Exception {
        Engine.TLEnvironment stdEnv = Engine.defaultEnvironment();
        assertEquals("()", engine.execute("()", stdEnv).toString());
        assertEquals("(1 2 3)", engine.execute("(list 1 2 3)", stdEnv).toString());
    }

    @Test
    public void testArrayString() throws Exception {
        Engine.TLEnvironment stdEnv = Engine.defaultEnvironment();
        assertEquals("[]", engine.execute("[]", stdEnv).toString());
        assertEquals("[1 2 3]", engine.execute("[1 2 3]", stdEnv).toString());
    }

    @Test
    public void testNull() throws Exception {
        assertNull(engine.execute("null", env).getValue());
        try {
            engine.execute("(def null 1)", env);
            fail("Can't redefine null");
        } catch (Exception ex) {
            // Should fail
        }
    }

    @Test
    public void testTrueFalse() throws Exception {
        assertEquals(true, engine.execute("true", env).getValue());
        assertEquals(false, engine.execute("false", env).getValue());
        try {
            engine.execute("(def true 1)", env);
            fail("Can't redefine true");
        } catch (Exception ex) {
            // Should fail
        }
        try {
            engine.execute("(def false 1)", env);
            fail("Can't redefine false");
        } catch (Exception ex) {
            // Should fail
        }
    }

    @Test
    public void testDefaultEnvironment() throws Exception {
        Engine.TLEnvironment stdEnv = Engine.defaultEnvironment();
        // +
        assertEquals("Double + Double",
                1.1, engine.execute("(+ 0.5 0.6)", stdEnv).getValue());
        assertEquals("Integer + Integer",
                5, engine.execute("(+ 2 3)", stdEnv).getValue());
        assertEquals("Double + Integer",
                1.1, engine.execute("(+ 0.1 1)", stdEnv).getValue());
        // -
        assertEquals("Double - Double",
                0.5 - 0.6, (double) engine.execute("(- 0.5 0.6)", stdEnv).getValue(), DELTA);
        assertEquals("Integer - Integer",
                -1, engine.execute("(- 2 3)", stdEnv).getValue());
        assertEquals("Double - Integer",
                0.1 - 1, engine.execute("(- 0.1 1)", stdEnv).getValue());
        // *
        assertEquals("Double * Double",
                0.5 * 0.6, engine.execute("(* 0.5 0.6)", stdEnv).getValue());
        assertEquals("Integer * Integer",
                6, engine.execute("(* 2 3)", stdEnv).getValue());
        assertEquals("Double * Integer",
                0.1 * 2, engine.execute("(* 0.1 2)", stdEnv).getValue());
        // /
        assertEquals("Double / Double",
                0.5 / 0.6, engine.execute("(/ 0.5 0.6)", stdEnv).getValue());
        assertEquals("Integer / Integer",
                1.5, engine.execute("(/ 3 2)", stdEnv).getValue());
        assertEquals("Double / Integer",
                0.1 / 2, engine.execute("(/ 0.1 2)", stdEnv).getValue());
        assertEquals("Integer / Integer = Integer",
            70 / 7, engine.execute("(/ 70 7)", stdEnv).getValue());
        // <
        assertTrue("Double < Double", engine.execute("(< 0.5 0.6)", stdEnv).asBoolean());
        assertFalse("Integer < Integer", engine.execute("(< 3 2)", stdEnv).asBoolean());
        assertTrue("Double < Integer", engine.execute("(< 0.1 2)", stdEnv).asBoolean());
        // >
        assertFalse("Double > Double", engine.execute("(> 0.5 0.6)", stdEnv).asBoolean());
        assertTrue("Integer > Integer", engine.execute("(> 3 2)", stdEnv).asBoolean());
        assertFalse("Double > Integer", engine.execute("(> 0.1 2)", stdEnv).asBoolean());
        // <=
        assertTrue("Double <= Double", engine.execute("(<= 0.5 0.6)", stdEnv).asBoolean());
        assertTrue("Double <= Double", engine.execute("(<= 0.6 0.6)", stdEnv).asBoolean());
        assertFalse("Integer <= Integer", engine.execute("(<= 3 2)", stdEnv).asBoolean());
        assertTrue("Integer <= Integer", engine.execute("(<= 3 3)", stdEnv).asBoolean());
        assertTrue("Double <= Integer", engine.execute("(<= 0.1 2)", stdEnv).asBoolean());
        assertTrue("Double <= Integer", engine.execute("(<= 2.0 2)", stdEnv).asBoolean());
        // >=
        assertFalse("Double >= Double", engine.execute("(>= 0.5 0.6)", stdEnv).asBoolean());
        assertTrue("Double >= Double", engine.execute("(>= 0.6 0.6)", stdEnv).asBoolean());
        assertTrue("Integer >= Integer", engine.execute("(>= 3 2)", stdEnv).asBoolean());
        assertTrue("Integer >= Integer", engine.execute("(>= 3 3)", stdEnv).asBoolean());
        assertFalse("Double >= Integer", engine.execute("(>= 0.1 2)", stdEnv).asBoolean());
        assertTrue("Double >= Integer",  engine.execute("(>= 2.0 2)", stdEnv).asBoolean());
        // is
        Object obj = new Object();
        stdEnv.put(Engine.TLSymbolExpression.of("obj"), Engine.expressionOf(obj));
        stdEnv.put(Engine.TLSymbolExpression.of("obj2"), Engine.expressionOf(obj));
        stdEnv.put(Engine.TLSymbolExpression.of("obj3"), Engine.expressionOf(new Object()));
        assertTrue(engine.execute("(is obj obj2)", stdEnv).asBoolean());
        assertFalse(engine.execute("(is obj obj3)", stdEnv).asBoolean());
        assertFalse(engine.execute("(is \"foo\" \"foo\")", stdEnv).asBoolean());
        // eq
        assertTrue(engine.execute("(eq 1 1)", stdEnv).asBoolean());
        assertFalse(engine.execute("(eq 1 2)", stdEnv).asBoolean());
        assertTrue(engine.execute("(eq \"foo\" \"foo\")", stdEnv).asBoolean());
        assertTrue(engine.execute("(is eq =)", stdEnv).asBoolean());
        // car
        assertEquals(1, engine.execute("(car (quote (1)))", stdEnv).getValue());
        assertEquals(1, engine.execute("(car (quote (1 2 3 4)))", stdEnv).getValue());
        // cdr
        assertEquals(Collections.emptyList(), engine.execute("(cdr (quote (1)))", stdEnv).getValue());
        assertEquals(Arrays.asList(2, 3, 4), engine.execute("(cdr (quote (1 2 3 4)))", stdEnv).getValue());
        // cons
        assertEquals(Arrays.asList(1, 2), engine.execute("(cons 1 2)", stdEnv).getValue());
        assertEquals(Arrays.asList(0, 1, 2, 3), engine.execute("(cons 0 (quote (1 2 3)))", stdEnv).getValue());
        // length
        assertEquals(0, engine.execute("(length ())", stdEnv).getValue());
        assertEquals(3, engine.execute("(length (quote (1 2 3)))", stdEnv).getValue());
        assertEquals(3, engine.execute("(length [1 2 3])", stdEnv).getValue());
        // list
        assertEquals(Arrays.asList(1, 2, 3), engine.execute("(list 1 2 3)", stdEnv).getValue());
        assertEquals(Arrays.asList(1, 2, 6), engine.execute("(list 1 2 (+ 1 2 3))", stdEnv).getValue());
        // map
        assertEquals(Arrays.asList(2, 3, 4),
                engine.execute("(map (lambda (x) (+ x 1)) (list 1 2 3))", stdEnv).getValue());
        // nth
        assertEquals(1, engine.execute("(nth 1 [0 1 2])", stdEnv).getValue());
        assertEquals(1, engine.execute("(nth 1 (list 0 1 2))", stdEnv).getValue());
    }

    @Test
    public void testBigNumbers() throws Exception {
        Engine.TLEnvironment stdEnv = Engine.defaultEnvironment();
        stdEnv.put(Engine.TLSymbolExpression.of("INT_MAX"), Engine.expressionOf(Integer.MAX_VALUE));
        stdEnv.put(Engine.TLSymbolExpression.of("INT_MIN"), Engine.expressionOf(Integer.MIN_VALUE));
        stdEnv.put(Engine.TLSymbolExpression.of("LONG_MAX"), Engine.expressionOf(BigDecimal.valueOf(Long.MAX_VALUE)));
        stdEnv.put(Engine.TLSymbolExpression.of("LONG_MIN"), Engine.expressionOf(BigDecimal.valueOf(Long.MIN_VALUE)));
        stdEnv.put(Engine.TLSymbolExpression.of("DOUBLE_MAX"), Engine.expressionOf(Double.MAX_VALUE));
        stdEnv.put(Engine.TLSymbolExpression.of("DOUBLE_MIN"), Engine.expressionOf(Double.MIN_VALUE));
        BigDecimal intMaxPlusOne = BigDecimal.valueOf(Integer.MAX_VALUE).add(BigDecimal.ONE);
        assertEquals(intMaxPlusOne, engine.execute("(+ INT_MAX 1)", stdEnv).getValue());
        BigDecimal intMinMinusOne = BigDecimal.valueOf(Integer.MIN_VALUE).subtract(BigDecimal.ONE);
        assertEquals(intMinMinusOne, engine.execute("(- INT_MIN 1)", stdEnv).getValue());
        BigDecimal intMaxTimesTwo = BigDecimal.valueOf(Integer.MAX_VALUE).multiply(BigDecimal.valueOf(2));
        assertEquals(intMaxTimesTwo, engine.execute("(* 2 INT_MAX)", stdEnv).getValue());
        BigDecimal intMinTimesTwo = BigDecimal.valueOf(Integer.MIN_VALUE).multiply(BigDecimal.valueOf(2));
        assertEquals(intMinTimesTwo, engine.execute("(* INT_MIN 2)", stdEnv).getValue());
        BigDecimal intMaxDivHalf = BigDecimal.valueOf(Integer.MAX_VALUE).divide(BigDecimal.valueOf(0.5), RoundingMode.UP);
        assertEquals(intMaxDivHalf, engine.execute("(/ INT_MAX 0.5)", stdEnv).getValue());
        BigDecimal intMinDivHalf = BigDecimal.valueOf(Integer.MIN_VALUE).divide(BigDecimal.valueOf(0.5), RoundingMode.UP);
        assertEquals(intMinDivHalf, engine.execute("(/ INT_MIN 0.5)", stdEnv).getValue());
        BigDecimal longMaxPlusOne = BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE);
        assertEquals(longMaxPlusOne, engine.execute("(+ LONG_MAX 1)", stdEnv).getValue());
        BigDecimal longMinMinusOne = BigDecimal.valueOf(Long.MIN_VALUE).subtract(BigDecimal.ONE);
        assertEquals(longMinMinusOne, engine.execute("(- LONG_MIN 1)", stdEnv).getValue());
        BigDecimal longMaxTimesTwo = BigDecimal.valueOf(Long.MAX_VALUE).multiply(BigDecimal.valueOf(2));
        assertEquals(longMaxTimesTwo, engine.execute("(* 2 LONG_MAX)", stdEnv).getValue());
        BigDecimal longMinTimesTwo = BigDecimal.valueOf(Long.MIN_VALUE).multiply(BigDecimal.valueOf(2));
        assertEquals(longMinTimesTwo, engine.execute("(* LONG_MIN 2)", stdEnv).getValue());
        BigDecimal longMaxDivHalf = BigDecimal.valueOf(Long.MAX_VALUE).divide(BigDecimal.valueOf(0.5), RoundingMode.UP);
        assertEquals(longMaxDivHalf, engine.execute("(/ LONG_MAX 0.5)", stdEnv).getValue());
        BigDecimal longMinDivHalf = BigDecimal.valueOf(Long.MIN_VALUE).divide(BigDecimal.valueOf(0.5), RoundingMode.UP);
        assertEquals(longMinDivHalf, engine.execute("(/ LONG_MIN 0.5)", stdEnv).getValue());
        BigDecimal doubleMaxPlusOne = BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.ONE);
        assertEquals(doubleMaxPlusOne, engine.execute("(+ DOUBLE_MAX 1)", stdEnv).getValue());
        BigDecimal doubleMaxTimesTwo = BigDecimal.valueOf(Double.MAX_VALUE).multiply(BigDecimal.valueOf(2));
        assertEquals(Engine.reduceBigDecimal(doubleMaxTimesTwo), engine.execute("(* 2 DOUBLE_MAX)", stdEnv).getValue());
        BigDecimal doubleMaxDivHalf = BigDecimal.valueOf(Double.MAX_VALUE).divide(BigDecimal.valueOf(0.5), RoundingMode.UP);
        assertEquals(Engine.reduceBigDecimal(doubleMaxDivHalf), engine.execute("(/ DOUBLE_MAX 0.5)", stdEnv).getValue());
        engine.execute("(def fact (lambda (n) (if (<= n 1) 1 (* n (fact (- n 1))))))", stdEnv);
        assertEquals(new BigDecimal("93326215443944152681699238856266700490715968264381621468592963895217599993229915608941463976156518286253697920827223758251185210916864000000000000000000000000"),
                engine.execute("(fact 100)", stdEnv).getValue());
    }
}

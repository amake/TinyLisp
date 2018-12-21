package org.tinylisp.engine;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

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
                    result += (Integer) ((Engine.TLAtomExpression) arg).getValue();
                }
                return Engine.TLJavaObjectExpression.of(result);
            }
        });
        env.put(Engine.TLSymbolExpression.of("addArray"), new Engine.TLFunction() {
            @Override public Engine.TLExpression invoke(Engine.TLListExpression args) {
                int result = 0;
                for (Engine.TLExpression arg : args) {
                    for (Object n : ((Engine.TLArrayExpression) arg).getValue()) {
                        result += (Integer) n;
                    }
                }
                return Engine.TLJavaObjectExpression.of(result);
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

        assertEquals(Engine.TLJavaObjectExpression.of(6), engine.evaluate(exp, env));
    }

    @Test
    public void testExecute() throws Exception {
        assertEquals(10, engine.execute("(add 1 2 3 4)", env));
    }

    @Test
    public void testArrays() throws Exception {
        assertEquals(15, engine.execute("(addArray [1 2 3 4 5])", env));
        assertEquals(30, engine.execute("(addArray [1 2 3 4 5] [1 2 3 4 5])", env));
    }

    @Test
    public void testMethodFunction() throws Exception {
        Method method = Integer.class.getMethod("toString", int.class, int.class);
        Engine.TLMethodFunction split = Engine.TLMethodFunction.of(null, method);
        env.put(Engine.TLSymbolExpression.of("toString"), split);
        assertEquals("b", engine.execute("(toString 11 16)", env));
    }

    @Test
    public void testSet() throws Exception {
        Object result = engine.execute("(set foo 123)", env);
        assertNull("Result of `set' is null", result);
        assertTrue(env.containsKey(Engine.TLSymbolExpression.of("foo")));
        assertEquals(Engine.TLNumberExpression.of(123), env.get(Engine.TLSymbolExpression.of("foo")));
    }

    @Test
    public void testLambda() throws Exception {
        assertEquals(2, engine.execute("((lambda (x) (add 1 x)) 1)", env));
        engine.execute("(set increment (lambda (x) (add 1 x)))", env);
        assertEquals(5, engine.execute("(increment 4)", env));
    }
}

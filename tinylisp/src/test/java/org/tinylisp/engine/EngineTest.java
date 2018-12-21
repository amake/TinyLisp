package org.tinylisp.engine;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

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
}

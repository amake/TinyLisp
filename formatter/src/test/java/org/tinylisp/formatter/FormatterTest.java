package org.tinylisp.formatter;

import org.junit.Test;

import static org.junit.Assert.*;

public class FormatterTest {
    @Test public void testFormat() {
        Formatter formatter = new Formatter();
        formatter.addVisitor(new Formatter.Visitor() {
            @Override public void visit(Formatter.TLAggregateToken parent, Formatter.TLToken child, int depth) {
                if (child instanceof Formatter.TLAtomToken) {
                    ((Formatter.TLAtomToken) child).value += '!';
                }
            }
        });
        assertEquals("(!a! !b! !c!)!", formatter.format("(a b c)"));
        formatter = new Formatter();
        assertEquals("(let ((foo (+ 1 1)))\n (bar)\n (baz))", formatter.format("(let ((foo (+ 1 1))) (bar) (baz))"));
        assertEquals("(let ((foo 1)\n (bar 2))\n (baz)\n (buzz))", formatter.format("(let ((foo 1) (bar 2)) (baz) (buzz))"));
        assertEquals("(let ((foo (+ 1 1)))\n (bar)\n (baz))", formatter.format("(let ((foo (+ 1 1))) (bar) (baz))"));
        assertEquals("(if a\n  b)", formatter.format("(if a b)"));
        assertEquals("(if a\n  b\n c\n d)", formatter.format("(if a b c d)"));
    }
}

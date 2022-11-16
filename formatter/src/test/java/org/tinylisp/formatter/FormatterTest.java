package org.tinylisp.formatter;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class FormatterTest {

    private Formatter formatter;

    @Before public void setUp() {
        formatter = new Formatter();
    }

    @Test public void testCustomVisitor() {
        formatter.addVisitor((parent, child, depth) -> {
            if (child instanceof Formatter.TLAtomToken) {
                ((Formatter.TLAtomToken) child).value += '!';
            }
        });
        assertEquals("(!a! !b! !c!)!", formatter.format("(a b c)"));
    }

    @Test public void testFormat() {
        assertEquals("(let ((foo (+ 1 1)))\n (bar)\n (baz))", formatter.format("(let ((foo (+ 1 1))) (bar) (baz))"));
        assertEquals("(let ((foo 1)\n      (bar 2))\n (baz)\n (buzz))", formatter.format("(let ((foo 1) (bar 2)) (baz) (buzz))"));
        assertEquals("(let ((foo (+ 1 1)))\n (bar)\n (baz))", formatter.format("(let ((foo (+ 1 1))) (bar) (baz))"));
        assertEquals("(if a\n  b)", formatter.format("(if a b)"));
        assertEquals("(if a\n  b\n c\n d)", formatter.format("(if a b c d)"));
        assertEquals("(let ((a 1)\n      (b 2))\n (if (> a b)\n   'foo\n  'bar))",
                formatter.format("(let ((a 1) (b 2)) (if (> a b) 'foo 'bar))"));
        assertEquals("(if a\n  (let ((a 1)\n        (b 2))\n   'foo\n   'bar)\n baz)",
                formatter.format("(if a (let ((a 1)(b 2)) 'foo 'bar) baz)"));
        assertEquals("(progn\n a\n b\n c\n d)", formatter.format("(progn a b c d)"));
        assertEquals("(a b c [1 2 3] 'foo)", formatter.format("( a b c [ 1 2 3] ' foo)"));
        assertEquals("((a b c) [1 2 3] 'foo (a b c))", formatter.format("((a b c)[ 1 2 3]'foo(a b c))"));
        assertEquals("(lambda (x y z)\n 'foo)", formatter.format("(lambda (x y z) 'foo)"));
        assertEquals("(lambda (x y z)\n 'foo\n 'bar)", formatter.format("(lambda (x y z) 'foo 'bar)"));
        assertEquals("(map (lambda (n)\n      (+ n 1))\n '(1 2 3))", formatter.format("(map (lambda (n) (+ n 1)) '(1 2 3))"));
    }

    @Test public void testComments() {
        assertEquals("; blah", formatter.format("; blah"));
        assertEquals("(; foo\n)", formatter.format("(  ; foo\n)"));
        assertEquals("(a ; foo\n )", formatter.format("(a  ; foo\n  )"));
        assertEquals("(a\n ;; foo\n )", formatter.format("(a  ;; foo\n  )"));
    }

    @Test public void testPartialInput() {
        assertEquals("", formatter.format(""));
        assertEquals("(", formatter.format("("));
        assertEquals(")", formatter.format(")"));
        assertEquals("()", formatter.format("()"));
        assertEquals("(if a\n )", formatter.format("(if a )"));
        assertEquals("(if a", formatter.format("(if a"));
    }

    @Test public void testIdempotency() {
        assertEquals("(if a\n )", formatter.format("(if a\n )"));
        assertEquals("\"\"", formatter.format("\"\""));
    }
}

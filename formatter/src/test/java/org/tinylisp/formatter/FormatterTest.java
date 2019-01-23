package org.tinylisp.formatter;

import org.junit.Test;

import static org.junit.Assert.*;

public class FormatterTest {
    @Test public void testFormat() {
        Formatter formatter = new Formatter();
        assertEquals("(!a! !b! !c!)!", formatter.format("(a b c)"));
    }
}

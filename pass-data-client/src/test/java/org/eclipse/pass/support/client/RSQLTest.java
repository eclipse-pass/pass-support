package org.eclipse.pass.support.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class RSQLTest {
    @Test
    public void testOperations() {
        assertEquals("name=='value'", RSQL.equals("name", "value"));
        assertEquals("name==''", RSQL.equals("name", ""));
        assertEquals("name=in=('value1','value2')", RSQL.in("name", "value1", "value2"));
        assertEquals("name=out=('value1','value2')", RSQL.out("name", "value1", "value2"));
        assertEquals("name!='value'", RSQL.notEquals("name", "value"));
    }

    @Test
    public void testGrouping() {
        assertEquals("(name1=='value1';name2=='value2')",
                RSQL.and(RSQL.equals("name1", "value1"), RSQL.equals("name2", "value2")));
        assertEquals("(name1=='value1',name2=='value2')",
                RSQL.or(RSQL.equals("name1", "value1"), RSQL.equals("name2", "value2")));
    }

    @Test
    public void testEscape() {
        assertEquals("name=='v\\\\a\\'lu\\\"e'", RSQL.equals("name", "v\\a'lu\"e"));
    }
}

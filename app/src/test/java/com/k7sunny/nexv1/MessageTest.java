package com.k7sunny.nexv1;

import org.junit.Test;
import static org.junit.Assert.*;

public class MessageTest {

    @Test
    public void testMessageConstructorAndGetters() {
        Message message = new Message("Hello User", Message.TYPE_USER);
        assertEquals("Hello User", message.getText());
        assertEquals(Message.TYPE_USER, message.getType());
        assertFalse(message.isActionsVisible());
        assertNull(message.getMemoryTag());
    }

    @Test
    public void testSetters() {
        Message message = new Message("Hello", Message.TYPE_TYPING);
        message.setText("New text");
        message.setType(Message.TYPE_AI);
        message.setActionsVisible(true);
        message.setMemoryTag("Memory: Pets");

        assertEquals("New text", message.getText());
        assertEquals(Message.TYPE_AI, message.getType());
        assertTrue(message.isActionsVisible());
        assertEquals("Memory: Pets", message.getMemoryTag());
    }
}

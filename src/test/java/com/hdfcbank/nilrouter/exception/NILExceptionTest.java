package com.hdfcbank.nilrouter.exception;

import com.hdfcbank.nilrouter.model.Fault;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NILExceptionTest {

    @Test
    void testDefaultConstructor() {
        NILException ex = new NILException();
        assertNull(ex.getMessage());
        assertNull(ex.getErrors());
    }

    @Test
    void testConstructorWithMessage() {
        NILException ex = new NILException("error message");
        assertEquals("error message", ex.getMessage());
        assertNull(ex.getErrors());
    }

    @Test
    void testConstructorWithMessageAndThrowable() {
        Throwable cause = new RuntimeException("cause");
        NILException ex = new NILException("error message", cause);
        assertEquals("error message", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertNull(ex.getErrors());
    }

    @Test
    void testConstructorWithMessageAndErrors() {
        Fault fault = Fault.builder().build();
        List<Fault> faults = Collections.singletonList(fault);
        NILException ex = new NILException("error message", faults);
        assertEquals("error message", ex.getMessage());
        assertEquals(faults, ex.getErrors());
    }

    @Test
    void testSetAndGetErrors() {
        Fault fault1 = Fault.builder().build();
        Fault fault2 = Fault.builder().build();
        List<Fault> faults = Arrays.asList(fault1, fault2);
        NILException ex = new NILException();
        ex.setErrors(faults);
        assertEquals(faults, ex.getErrors());
    }
}


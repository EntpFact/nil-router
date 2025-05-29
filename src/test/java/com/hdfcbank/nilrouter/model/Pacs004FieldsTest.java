package com.hdfcbank.nilrouter.model;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pacs004FieldsTest {

    @Test
    void testAllArgsConstructorAndGetters() {
        Pacs004Fields fields = new Pacs004Fields(
                "BIZ123",
                "E2E456",
                "TX789",
                "1000.00",
                "SWITCH-A",
                "BATCH001"
        );

        assertEquals("BIZ123", fields.getBizMsgIdr());
        assertEquals("E2E456", fields.getEndToEndId());
        assertEquals("TX789", fields.getTxId());
        assertEquals("1000.00", fields.getAmount());
        assertEquals("SWITCH-A", fields.getSwtch());
        assertEquals("BATCH001", fields.getBatchId());
    }

    @Test
    void testToString() {
        Pacs004Fields fields = new Pacs004Fields(
                "BIZ123",
                "E2E456",
                "TX789",
                "1000.00",
                "SWITCH-A",
                "BATCH001"
        );

        String toStringOutput = fields.toString();
        assertTrue(toStringOutput.contains("BIZ123"));
        assertTrue(toStringOutput.contains("E2E456"));
        assertTrue(toStringOutput.contains("TX789"));
        assertTrue(toStringOutput.contains("1000.00"));
        assertTrue(toStringOutput.contains("SWITCH-A"));
        assertTrue(toStringOutput.contains("BATCH001"));
    }

    @Test
    void testSetters() {
        Pacs004Fields fields = new Pacs004Fields(
                null, null, null, null, null, null
        );

        fields.setBizMsgIdr("BIZ999");
        fields.setEndToEndId("E2E888");
        fields.setTxId("TX777");
        fields.setAmount("500.00");
        fields.setSwtch("SWITCH-B");
        fields.setBatchId("BATCH999");

        assertEquals("BIZ999", fields.getBizMsgIdr());
        assertEquals("E2E888", fields.getEndToEndId());
        assertEquals("TX777", fields.getTxId());
        assertEquals("500.00", fields.getAmount());
        assertEquals("SWITCH-B", fields.getSwtch());
        assertEquals("BATCH999", fields.getBatchId());
    }
}


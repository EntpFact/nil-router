package com.hdfcbank.nilrouter.util;


import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class UtilityMethodsTest {

    private UtilityMethods utilityMethods;
    private DocumentBuilderFactory factory;
    private DocumentBuilder builder;

    @BeforeEach
    void setup() throws Exception {
        utilityMethods = new UtilityMethods();
        factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        builder = factory.newDocumentBuilder();
    }

    @Test
    void testGetBizMsgIdr_shouldReturnCorrectValue() throws Exception {
        String xml = "<AppHdr><BizMsgIdr>ABC123456789</BizMsgIdr></AppHdr>";
        Document document = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        String result = utilityMethods.getBizMsgIdr(document);
        assertEquals("ABC123456789", result);
    }

    @Test
    void testGetMsgDefIdr_shouldReturnCorrectValue() throws Exception {
        String xml = "<AppHdr><MsgDefIdr>pacs.004.001.10</MsgDefIdr></AppHdr>";
        Document document = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        String result = utilityMethods.getMsgDefIdr(document);
        assertEquals("pacs.004.001.10", result);
    }

    @Test
    void testIsOutward_shouldReturnTrue() {
        String xml = """
            <Document>
                <Fr>HDFCXYZ</Fr>
                <To>RBIABC</To>
            </Document>
        """;
        assertTrue(utilityMethods.isOutward(xml));
    }

    @Test
    void testIsOutward_shouldReturnFalse_whenFromIsNotHDFC() {
        String xml = """
            <Document>
                <Fr>SBIABC</Fr>
                <To>RBIABC</To>
            </Document>
        """;
        assertFalse(utilityMethods.isOutward(xml));
    }

    @Test
    void testIsOutward_shouldReturnFalse_whenToIsNotRBI() {
        String xml = """
            <Document>
                <Fr>HDFCABC</Fr>
                <To>XYZBANK</To>
            </Document>
        """;
        assertFalse(utilityMethods.isOutward(xml));
    }

    @Test
    void testIsOutward_shouldReturnFalse_onMalformedXml() {
        String xml = "<Document><Fr>HDFC</Fr>"; // malformed
        assertFalse(utilityMethods.isOutward(xml));
    }
}

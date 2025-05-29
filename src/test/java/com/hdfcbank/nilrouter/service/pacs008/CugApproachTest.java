package com.hdfcbank.nilrouter.service.pacs008;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CugApproachTest {
    @InjectMocks
    private CugApproach cugApproach;

    @Mock
    private KafkaUtils kafkaUtils;
    @Mock
    private UtilityMethods utilityMethods;
    @Mock
    private NilRepository nilRepository;
    @Mock
    private ObjectMapper objectMapper;


    @Test
    void testProcessCugApproach_AllFc() throws Exception {
        String xml = getSampleXml(false);
        mockUtilityMethods(xml, 2);
        when(nilRepository.cugAccountExists(any())).thenReturn(false);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        cugApproach.processCugApproach(xml);
        // No exception means pass, verify mocks
        verify(utilityMethods, atLeastOnce()).getBizMsgIdr(any());
        verify(utilityMethods, atLeastOnce()).getMsgDefIdr(any());
        verify(utilityMethods, atLeastOnce()).getTotalAmount(any());
    }

    @Test
    void testProcessCugApproach_AllEph() throws Exception {
        String xml = getSampleXml(true);
        mockUtilityMethods(xml, 2);
        when(nilRepository.cugAccountExists(any())).thenReturn(true);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        cugApproach.processCugApproach(xml);
        verify(utilityMethods, atLeastOnce()).getBizMsgIdr(any());
        verify(utilityMethods, atLeastOnce()).getMsgDefIdr(any());
        verify(utilityMethods, atLeastOnce()).getTotalAmount(any());
    }

    @Test
    void testProcessCugApproach_Mixed() throws Exception {
        String xml = getSampleXml(false);
        mockUtilityMethods(xml, 2);
        when(nilRepository.cugAccountExists(any())).thenReturn(true, false);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        cugApproach.processCugApproach(xml);
        verify(utilityMethods, atLeastOnce()).getBizMsgIdr(any());
        verify(utilityMethods, atLeastOnce()).getMsgDefIdr(any());
        verify(utilityMethods, atLeastOnce()).getTotalAmount(any());
    }

    @Test
    void testIsValidCugAccount_TrueAndFalse() throws Exception {
        String xml = getSampleXml(false);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        NodeList txNodes = doc.getElementsByTagNameNS("*", "CdtTrfTxInf");
        Node tx = txNodes.item(0);
        when(nilRepository.cugAccountExists(any())).thenReturn(true);
        boolean result = ReflectionTestUtils.invokeMethod(cugApproach, "isValidCugAccount", tx);
        assertTrue(result);
        when(nilRepository.cugAccountExists(any())).thenReturn(false);
        result = ReflectionTestUtils.invokeMethod(cugApproach, "isValidCugAccount", tx);
        assertFalse(result);
    }

    @Test
    void testBuildNewXml() throws Exception {
        String xml = getSampleXml(false);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        NodeList txNodes = doc.getElementsByTagNameNS("*", "CdtTrfTxInf");
        java.util.List<Node> txList = new java.util.ArrayList<>();
        txList.add(txNodes.item(0));
        String resultXml = ReflectionTestUtils.invokeMethod(cugApproach, "buildNewXml", doc, dBuilder, txList);
        assertNotNull(resultXml);
        assertTrue(resultXml.contains("CdtTrfTxInf"));
    }

    @Test
    void testProcessCugApproach_Exception() throws Exception {
        // Simulate exception in utilityMethods.getBizMsgIdr
        String xml = getSampleXml(false);
        doThrow(new RuntimeException("fail")).when(utilityMethods).getBizMsgIdr(any());
        assertThrows(Exception.class, () -> cugApproach.processCugApproach(xml));
    }

    private void mockUtilityMethods(String xml, int txCount) throws Exception {
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("msgid");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("msgtype");
        when(utilityMethods.getTotalAmount(any())).thenReturn(BigDecimal.valueOf(100));
        when(utilityMethods.evaluateText(any(), any(), any())).thenReturn("10");
    }

    private String getSampleXml(boolean eph) {
        String accountId = eph ? "EPH123" : "FC123";
        return """
                <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">
                  <FIToFICstmrCdtTrf>
                    <CdtTrfTxInf>
                      <PmtId><EndToEndId>e2e1</EndToEndId><TxId>tx1</TxId></PmtId>
                      <IntrBkSttlmAmt>10</IntrBkSttlmAmt>
                      <CdtrAcct><Id><Othr><Id>" + accountId + "</Id></Othr></Id></CdtrAcct>
                      <RmtInf><Ustrd>batch1</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                    <CdtTrfTxInf>
                      <PmtId><EndToEndId>e2e2</EndToEndId><TxId>tx2</TxId></PmtId>
                      <IntrBkSttlmAmt>10</IntrBkSttlmAmt>
                      <CdtrAcct><Id><Othr><Id>" + accountId + "</Id></Othr></Id></CdtrAcct>
                      <RmtInf><Ustrd>batch2</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
    }
}



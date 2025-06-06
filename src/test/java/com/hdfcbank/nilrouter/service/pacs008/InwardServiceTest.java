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
class InwardServiceTest {
    @InjectMocks
    private InwardService inwardService;

    @Mock
    private NilRepository nilRepository;
    @Mock
    private KafkaUtils kafkaUtils;
    @Mock
    private UtilityMethods utilityMethods;
    @Mock
    private ObjectMapper objectMapper;



    @Test
    void testProcessFreshInward_FC() throws Exception {
        String xml = getSampleXml("HDFCN12345678901234001");
        mockUtilityMethods(xml, "RBIP202501176240070534", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        inwardService.processFreshInward(xml);
        verify(utilityMethods).getBizMsgIdr(any());
        verify(utilityMethods).getMsgDefIdr(any());
        verify(utilityMethods).getTotalAmount(any());
    }

    @Test
    void testProcessFreshInward_EPH() throws Exception {
        String xml = getSampleXml("HDFCN12345678901234007");
        mockUtilityMethods(xml, "RBIP202501176240070539", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        inwardService.processFreshInward(xml);
        verify(utilityMethods).getBizMsgIdr(any());
        verify(utilityMethods).getMsgDefIdr(any());
        verify(utilityMethods).getTotalAmount(any());
    }

    @Test
    void testProcessLateReturn_AllFC() throws Exception {
        String xml = getLateReturnXml("HDFCN12345678901234001", null, false);
        mockUtilityMethods(xml, "RBIP202501176240070534", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        inwardService.processLateReturn(xml);
        verify(utilityMethods, atLeastOnce()).getBizMsgIdr(any());
        verify(utilityMethods, atLeastOnce()).getMsgDefIdr(any());
        verify(utilityMethods, atLeastOnce()).getTotalAmount(any());
    }

    @Test
    void testProcessLateReturn_AllEPH() throws Exception {
        String xml = getLateReturnXml(null, "HDFCN12345678961234001", false);
        mockUtilityMethods(xml, "RBIP202501176240070539", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        inwardService.processLateReturn(xml);
        verify(utilityMethods, atLeastOnce()).getBizMsgIdr(any());
        verify(utilityMethods, atLeastOnce()).getMsgDefIdr(any());
        verify(utilityMethods, atLeastOnce()).getTotalAmount(any());
    }

    @Test
    void testProcessLateReturn_Mixed() throws Exception {
        String xml = getLateReturnXml("HDFCN12345678901234001", "HDFCN12345678961234001", true);
        mockUtilityMethods(xml, "RBIP202501176240070539", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        inwardService.processLateReturn(xml);
        verify(utilityMethods, atLeastOnce()).getBizMsgIdr(any());
        verify(utilityMethods, atLeastOnce()).getMsgDefIdr(any());
        verify(utilityMethods, atLeastOnce()).getTotalAmount(any());
    }

    @Test
    void testProcessLateReturn_AllFCandFresh() throws Exception {
        String xml = getLateReturnXml("HDFCN12345678901234001", null, true);
        mockUtilityMethods(xml, "RBIP202501176240070539", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        inwardService.processLateReturn(xml);
        verify(utilityMethods, atLeastOnce()).getBizMsgIdr(any());
        verify(utilityMethods, atLeastOnce()).getMsgDefIdr(any());
        verify(utilityMethods, atLeastOnce()).getTotalAmount(any());
    }

    @Test
    void testProcessLateReturn_AllEPHandFresh() throws Exception {
        String xml = getLateReturnXml(null, "HDFCN12345678961234001", true);
        mockUtilityMethods(xml, "RBIP202501176240070539", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        inwardService.processLateReturn(xml);
        verify(utilityMethods, atLeastOnce()).getBizMsgIdr(any());
        verify(utilityMethods, atLeastOnce()).getMsgDefIdr(any());
        verify(utilityMethods, atLeastOnce()).getTotalAmount(any());
    }


    @Test
    void testExtractTransactionIdentifierInstrInf_and_extractIdFromText() throws Exception {
        String xml = getSampleXml("HDFCN12345678901234001");
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        NodeList txNodes = doc.getElementsByTagNameNS("*", "CdtTrfTxInf");
        Node tx = txNodes.item(0);
        javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        String id = ReflectionTestUtils.invokeMethod(inwardService, "extractTransactionIdentifier", tx, xpath);
        assertNotNull(id);
        String valid = ReflectionTestUtils.invokeMethod(inwardService, "extractIdFromText", "HDFCN12345678901234001");
        assertNotNull(valid);
        String invalid = ReflectionTestUtils.invokeMethod(inwardService, "extractIdFromText", "NOIDHERE");
        assertNull(invalid);
    }

    @Test
    void testExtractTransactionIdentifierUstrd_and_extractIdFromText() throws Exception {
        String xml = """
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09">
                  <FIToFICstmrCdtTrf>
                    <CdtTrfTxInf>
                      <PmtId><EndToEndId>e2e1</EndToEndId><TxId>tx1</TxId></PmtId>
                      <IntrBkSttlmAmt>10</IntrBkSttlmAmt>
                      <CdtrAcct><Id><Othr><Id>ACCT1</Id></Othr></Id></CdtrAcct>
                      <InstrForCdtrAgt><InstrInf>Example</InstrInf></InstrForCdtrAgt>
                      <RmtInf><Ustrd>HDFCN12345678901234001</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        NodeList txNodes = doc.getElementsByTagNameNS("*", "CdtTrfTxInf");
        Node tx = txNodes.item(0);
        javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        String id = ReflectionTestUtils.invokeMethod(inwardService, "extractTransactionIdentifier", tx, xpath);
        assertNotNull(id);
        String valid = ReflectionTestUtils.invokeMethod(inwardService, "extractIdFromText", "HDFCN12345678901234001");
        assertNotNull(valid);
        String invalid = ReflectionTestUtils.invokeMethod(inwardService, "extractIdFromText", "NOIDHERE");
        assertNull(invalid);
    }

    @Test
    void testBuildNewXml() throws Exception {
        String xml = getSampleXml("HDFCN1234567890123400");
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        NodeList txNodes = doc.getElementsByTagNameNS("*", "CdtTrfTxInf");
        java.util.List<Node> txList = new java.util.ArrayList<>();
        txList.add(txNodes.item(0));
        String resultXml = ReflectionTestUtils.invokeMethod(inwardService, "buildNewXml", doc, dBuilder, txList);
        assertNotNull(resultXml);
        assertTrue(resultXml.contains("CdtTrfTxInf"));
    }

    @Test
    void testProcessFreshInward_Exception() throws Exception {
        String xml = getSampleXml("HDFCN1234567890123400");
        doThrow(new RuntimeException("fail")).when(utilityMethods).getBizMsgIdr(any());
        assertThrows(Exception.class, () -> inwardService.processFreshInward(xml));
    }

    @Test
    void testProcessLateReturn_Exception() throws Exception {
        String xml = getLateReturnXml("HDFCN1234567890123400", null, false);
        doThrow(new RuntimeException("fail")).when(utilityMethods).getBizMsgIdr(any());
        assertThrows(Exception.class, () -> inwardService.processLateReturn(xml));
    }

    @Test
    void testProcessCugApproach_AllCugAccounts() throws Exception {
        String xml = """
                <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">
                  <FIToFICstmrCdtTrf>
                    <CdtTrfTxInf>
                      <PmtId><EndToEndId>e2e1</EndToEndId><TxId>tx1</TxId></PmtId>
                      <IntrBkSttlmAmt>10</IntrBkSttlmAmt>
                      <CdtrAcct><Id><Othr><Id>ACCT1</Id></Othr></Id></CdtrAcct>
                      <InstrForCdtrAgt><InstrInf>Info1</InstrInf></InstrForCdtrAgt>
                      <RmtInf><Ustrd>batch1</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
        mockUtilityMethods(xml, "RBIP202501176240070534", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        when(nilRepository.cugAccountExists(any())).thenReturn(true);
        inwardService.processCugApproach(xml);
        verify(kafkaUtils, atLeastOnce()).publishToResponseTopic(any(), any());
    }

    @Test
    void testProcessCugApproach_NoCugAccounts() throws Exception {
        String xml = """
                <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">
                  <FIToFICstmrCdtTrf>
                    <CdtTrfTxInf>
                      <PmtId><EndToEndId>e2e1</EndToEndId><TxId>tx1</TxId></PmtId>
                      <IntrBkSttlmAmt>10</IntrBkSttlmAmt>
                      <CdtrAcct><Id><Othr><Id>ACCT2</Id></Othr></Id></CdtrAcct>
                      <InstrForCdtrAgt><InstrInf>Info2</InstrInf></InstrForCdtrAgt>
                      <RmtInf><Ustrd>batch2</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
        mockUtilityMethods(xml, "RBIP202501176240070535", "pacs.008.001.09", BigDecimal.TEN);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        when(nilRepository.cugAccountExists(any())).thenReturn(false);
        inwardService.processCugApproach(xml);
        verify(kafkaUtils, atLeastOnce()).publishToResponseTopic(any(), any());
    }

    @Test
    void testProcessCugApproach_MixedCugAccounts() throws Exception {
        String xml = """
                <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">
                  <FIToFICstmrCdtTrf>
                    <CdtTrfTxInf>
                      <PmtId><EndToEndId>e2e1</EndToEndId><TxId>tx1</TxId></PmtId>
                      <IntrBkSttlmAmt>10</IntrBkSttlmAmt>
                      <CdtrAcct><Id><Othr><Id>ACCT1</Id></Othr></Id></CdtrAcct>
                      <InstrForCdtrAgt><InstrInf>Info1</InstrInf></InstrForCdtrAgt>
                      <RmtInf><Ustrd>batch1</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                    <CdtTrfTxInf>
                      <PmtId><EndToEndId>e2e2</EndToEndId><TxId>tx2</TxId></PmtId>
                      <IntrBkSttlmAmt>20</IntrBkSttlmAmt>
                      <CdtrAcct><Id><Othr><Id>ACCT2</Id></Othr></Id></CdtrAcct>
                      <InstrForCdtrAgt><InstrInf>Info2</InstrInf></InstrForCdtrAgt>
                      <RmtInf><Ustrd>batch2</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """;
        mockUtilityMethods(xml, "RBIP202501176240070536", "pacs.008.001.09", new BigDecimal(30));
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        when(nilRepository.cugAccountExists(any())).thenReturn(true, false);
        inwardService.processCugApproach(xml);
        verify(kafkaUtils, atLeastOnce()).publishToResponseTopic(any(), any());
    }

    @Test
    void testIsValidCugAccount_True() throws Exception {
        String xml = """
                <CdtTrfTxInf>
                  <CdtrAcct><Id><Othr><Id>ACCT1</Id></Othr></Id></CdtrAcct>
                </CdtTrfTxInf>
                """;
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        Node tx = doc.getDocumentElement();
        when(nilRepository.cugAccountExists("ACCT1")).thenReturn(true);
        boolean result = ReflectionTestUtils.invokeMethod(inwardService, "isValidCugAccount", tx);
        assertTrue(result);
    }

    @Test
    void testIsValidCugAccount_False() throws Exception {
        String xml = """
                <CdtTrfTxInf>
                  <CdtrAcct><Id><Othr><Id>ACCT2</Id></Othr></Id></CdtrAcct>
                </CdtTrfTxInf>
                """;
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));
        Node tx = doc.getDocumentElement();
        when(nilRepository.cugAccountExists("ACCT2")).thenReturn(false);
        boolean result = ReflectionTestUtils.invokeMethod(inwardService, "isValidCugAccount", tx);
        assertFalse(result);
    }

    private void mockUtilityMethods(String xml, String msgId, String msgType, BigDecimal totalAmount) throws Exception {
        when(utilityMethods.getBizMsgIdr(any())).thenReturn(msgId);
        when(utilityMethods.getMsgDefIdr(any())).thenReturn(msgType);
        when(utilityMethods.getTotalAmount(any())).thenReturn(totalAmount);
        lenient().when(utilityMethods.evaluateText(any(), any(), any())).thenReturn("10");
    }

    private String getSampleXml(String id) {
        return String.format("""
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09">
                  <FIToFICstmrCdtTrf>
                    <CdtTrfTxInf>
                      <PmtId><EndToEndId>e2e1</EndToEndId><TxId>tx1</TxId></PmtId>
                      <IntrBkSttlmAmt>10</IntrBkSttlmAmt>
                      <CdtrAcct><Id><Othr><Id>ACCT1</Id></Othr></Id></CdtrAcct>
                      <InstrForCdtrAgt><InstrInf>%s</InstrInf></InstrForCdtrAgt>
                      <RmtInf><Ustrd>batch1</Ustrd></RmtInf>
                    </CdtTrfTxInf>
                  </FIToFICstmrCdtTrf>
                </Document>
                """, id);
    }


    private String getLateReturnXml(String fcId, String ephId, boolean fresh) {
        StringBuilder sb = new StringBuilder();
        sb.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\"><FIToFICstmrCdtTrf>");
        if (fcId != null) {
            sb.append("<CdtTrfTxInf><PmtId><EndToEndId>e2e1</EndToEndId><TxId>tx1</TxId></PmtId><IntrBkSttlmAmt>10</IntrBkSttlmAmt><CdtrAcct><Id><Othr><Id>ACCT1</Id></Othr></Id></CdtrAcct><InstrForCdtrAgt><InstrInf>").append(fcId).append("</InstrInf></InstrForCdtrAgt><RmtInf><Ustrd>batch1</Ustrd></RmtInf></CdtTrfTxInf>");
        }
        if (ephId != null) {
            sb.append("<CdtTrfTxInf><PmtId><EndToEndId>e2e2</EndToEndId><TxId>tx2</TxId></PmtId><IntrBkSttlmAmt>10</IntrBkSttlmAmt><CdtrAcct><Id><Othr><Id>ACCT2</Id></Othr></Id></CdtrAcct><InstrForCdtrAgt><InstrInf>").append(ephId).append("</InstrInf></InstrForCdtrAgt><RmtInf><Ustrd>batch2</Ustrd></RmtInf></CdtTrfTxInf>");
        }
        if (fresh) {
            sb.append("<CdtTrfTxInf><PmtId><EndToEndId>e2e2</EndToEndId><TxId>tx2</TxId></PmtId><IntrBkSttlmAmt>10</IntrBkSttlmAmt><CdtrAcct><Id><Othr><Id>ACCT2</Id></Othr></Id></CdtrAcct><InstrForCdtrAgt><InstrInf>").append("</InstrInf></InstrForCdtrAgt><RmtInf><Ustrd>batch2</Ustrd></RmtInf></CdtTrfTxInf>");

        }
        sb.append("</FIToFICstmrCdtTrf></Document>");
        return sb.toString();
    }
}

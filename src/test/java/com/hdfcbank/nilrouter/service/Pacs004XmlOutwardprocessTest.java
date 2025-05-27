package com.hdfcbank.nilrouter.service;

import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Pacs004Fields;
import com.hdfcbank.nilrouter.model.TransactionAudit;
import com.hdfcbank.nilrouter.service.pacs004.Pacs004XmlOutwardprocess;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class Pacs004XmlOutwardprocessTest {

    @InjectMocks
    private Pacs004XmlOutwardprocess outwardProcess;

    @Mock
    private NilRepository dao;

    @Mock
    private UtilityMethods utilityMethods;

    @Mock
    private KafkaUtils kafkaUtils;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static final String TEST_XML = """
        <RequestPayload>
            <AppHdr>
                <BizMsgIdr>TestBizMsgId</BizMsgIdr>
                <MsgDefIdr>pacs.004.001.10</MsgDefIdr>
            </AppHdr>
            <Document>
                <TxInf>
                    <OrgnlTxId>TX123456</OrgnlTxId>
                    <OrgnlEndToEndId>123456789012345</OrgnlEndToEndId>
                    <RtrdIntrBkSttlmAmt>1500.50</RtrdIntrBkSttlmAmt>
                    <OrgnlTxRef>
                        <UndrlygCstmrCdtTrf>
                            <RmtInf>BatchId12345</RmtInf>
                        </UndrlygCstmrCdtTrf>
                    </OrgnlTxRef>
                </TxInf>
            </Document>
        </RequestPayload>
        """;

    @Test
    public void testProcessXML() throws Exception {
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("TestBizMsgId");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");

        outwardProcess.processXML(TEST_XML);

        verify(dao).saveDataInMsgEventTracker(argThat(tracker ->
                tracker.getMsgId().equals("TestBizMsgId") &&
                        tracker.getIntermediateCount() == 1 &&
                        tracker.getConsolidateAmt().compareTo(BigDecimal.valueOf(1500.50)) == 0
        ));

        verify(dao).saveAllTransactionAudits(argThat(list ->
                list.size() == 1 &&
                        list.get(0).getEndToEndId().equals("123456789012345") &&
                        list.get(0).getTxnId().equals("TX123456")
        ));
    }

    @Test
    public void testExtractPacs004Transactions() throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(TEST_XML.getBytes()));

        Pacs004Fields field = new Pacs004Fields("TestBizMsgId", "123456789012345", "TX123456", "2500.00", "SFMS", "BatchId12345");
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("TestBizMsgId");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");

        List<TransactionAudit> audits = outwardProcess.extractPacs004Transactions(doc, TEST_XML, List.of(field));

        assertEquals(1, audits.size());
        TransactionAudit audit = audits.get(0);
        assertEquals("TestBizMsgId", audit.getMsgId());
        assertEquals("123456789012345", audit.getEndToEndId());
        assertEquals("TX123456", audit.getTxnId());
        assertEquals("BatchId12345", audit.getBatchId());
        assertEquals(BigDecimal.valueOf(2500.00), audit.getAmount());
        assertEquals("SFMS", audit.getTarget());
    }

    @Test
    public void testDocumentToXml() throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(TEST_XML.getBytes()));

        String xmlResult = outwardProcess.documentToXml(doc);

        assertTrue(xmlResult.contains("<BizMsgIdr>TestBizMsgId</BizMsgIdr>"));
        assertTrue(xmlResult.contains("<OrgnlEndToEndId>123456789012345</OrgnlEndToEndId>"));
    }
}


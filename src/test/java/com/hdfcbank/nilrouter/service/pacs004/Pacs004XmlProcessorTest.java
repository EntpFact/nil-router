package com.hdfcbank.nilrouter.service.pacs004;

import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.service.AuditService;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class Pacs004XmlProcessorTest {

    @InjectMocks
    private Pacs004XmlProcessor processor;

    @Mock
    private NilRepository dao;

    @Mock
    private UtilityMethods utilityMethods;

    @Mock
    private KafkaUtils kafkaUtils;

    @Mock
    private AuditService outwardService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        ReflectionTestUtils.setField(processor, "fcTopic", "test-fc-topic");
        ReflectionTestUtils.setField(processor, "ephTopic", "test-eph-topic");
        ReflectionTestUtils.setField(processor, "sfmstopic", "test-sfms-topic");
    }

    @Test
    public void testParseXml_OutwardFlow() throws Exception {
        String xml = "<RequestPayload><AppHdr><BizMsgIdr>ID123</BizMsgIdr><MsgDefIdr>pacs.004.001.10</MsgDefIdr></AppHdr><Document></Document></RequestPayload>";

        when(utilityMethods.isOutward(xml)).thenReturn(true);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("ID123");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");

        processor.parseXml(xml);
//        outwardService.auditData(xml);

        // Here you'd assert logs/output or internal calls. Since it prints JSON, just verify processing.
        verify(utilityMethods, times(1)).getBizMsgIdr(any(Document.class));
    }

    @Test
    public void testParseXml_InwardFlow() throws Exception {
        String xml = "<RequestPayload><AppHdr><BizMsgIdr>ID456</BizMsgIdr><MsgDefIdr>pacs.004.001.10</MsgDefIdr></AppHdr><Document><TxInf><OrgnlTxId>HDFCN52022062824954014</OrgnlTxId><OrgnlEndToEndId>123456789012345</OrgnlEndToEndId><RtrdIntrBkSttlmAmt>1000</RtrdIntrBkSttlmAmt></TxInf></Document></RequestPayload>";

        when(utilityMethods.isOutward(xml)).thenReturn(false);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("ID456");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");

        processor.parseXml(xml);

//        verify(dao, atLeastOnce()).saveDataInMsgEventTracker(any());
    }


    @Test
    public void testParseXml_InwardFlow2() throws Exception {
        String xml = "<RequestPayload><AppHdr><BizMsgIdr>ID456</BizMsgIdr><MsgDefIdr>pacs.004.001.10</MsgDefIdr></AppHdr><Document><TxInf><OrgnlTxId>HDFCN52022062824954014</OrgnlTxId><OrgnlEndToEndId>123456789012345</OrgnlEndToEndId><RtrdIntrBkSttlmAmt>1000</RtrdIntrBkSttlmAmt></TxInf><TxInf><OrgnlTxId>HDFCN52022062866954014</OrgnlTxId><OrgnlEndToEndId>123456789012345</OrgnlEndToEndId><RtrdIntrBkSttlmAmt>100</RtrdIntrBkSttlmAmt></TxInf></Document></RequestPayload>";

        when(utilityMethods.isOutward(xml)).thenReturn(false);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("ID456");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.004.001.10");

        processor.parseXml(xml);

//        verify(dao, atLeastOnce()).saveDataInMsgEventTracker(any());
    }


    @Test
    public void testDocumentToXml() throws Exception {
        String xml = "<root><child>value</child></root>";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new java.io.ByteArrayInputStream(xml.getBytes()));

        String xmlResult = processor.documentToXml(document);

        assertTrue(xmlResult.contains("<child>value</child>"));
    }


}

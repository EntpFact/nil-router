package com.hdfcbank.nilrouter.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.service.pacs002.Pacs002XmlProcessor;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Pacs002XmlProcessorTest {

    @InjectMocks
    private Pacs002XmlProcessor pacs002XmlProcessor;

    @Mock
    private NilRepository dao;

    @Mock
    private UtilityMethods utilityMethods;

    @Mock
    private KafkaUtils kafkaUtils;

    private String sampleXml;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Set test property values
        ReflectionTestUtils.setField(pacs002XmlProcessor, "sfmsTopic", "sfms-topic");
        ReflectionTestUtils.setField(pacs002XmlProcessor, "fcTopic", "fc-topic");
        ReflectionTestUtils.setField(pacs002XmlProcessor, "ephTopic", "eph-topic");
        ReflectionTestUtils.setField(pacs002XmlProcessor, "msgEventTrackerTopic", "msg-tracker-topic");

        // Sample Pacs002 XML with TxInfAndSts
        sampleXml = """
                <RequestPayload xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.09">
                    <AppHdr>
                        <BizMsgIdr>RBIP202204016000000001</BizMsgIdr>
                    </AppHdr>
                    <Document>
                    <FIToFIPmtStsRpt>
                    <GrpHdr>
                    <MsgId>RBIP202204016000000001</MsgId>
                    </GrpHdr>
                        <TxInfAndSts>
                           <StsId>RBIPN62022062832123456</StsId>
                           <OrgnlEndToEndId>/XUTR/HDFCH22194004232</OrgnlEndToEndId>
                           <OrgnlTxId>BION52022062824954013</OrgnlTxId>
                           <TxSts>RJCT</TxSts>
                            <StsRsnInf>
                            <Rsn>
                            <Cd>RJ10</Cd>
                            </Rsn>
                            <AddtlInf>BatchId:0007</AddtlInf>
                              </StsRsnInf>
                             </TxInfAndSts>
                             <TxInfAndSts>
                             <StsId>RBIPN62022062832123457</StsId>
                             <OrgnlEndToEndId>/XUTR/HDFCH22194004233</OrgnlEndToEndId>
                             <OrgnlTxId>HDFCN52022062824954014</OrgnlTxId>
                              <TxSts>RJCT</TxSts>
                             <StsRsnInf>
                             <Rsn>
                             <Cd>RJ10</Cd>
                             </Rsn>
                             <AddtlInf>BatchId:0007</AddtlInf>
                             </StsRsnInf>
                             </TxInfAndSts>
                   </FIToFIPmtStsRpt>
                   </Document>                        
                </RequestPayload>
                """;


    }

    @Test
    void testProcessXML_withValidXmlFC_shouldPublishToKafka() throws Exception {
        // Arrange: Your sample pacs.002 XML
        String validXml = """
                <RequestPayload xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.09">
                                     <AppHdr>
                                         <BizMsgIdr>RBIP202204016000000001</BizMsgIdr>
                                     </AppHdr>
                                     <Document>
                                         <FIToFIPmtStsRpt>
                                             <GrpHdr>
                                                 <MsgId>RBIP202204016000000001</MsgId>
                                             </GrpHdr>
                                             <TxInfAndSts>
                                                 <StsId>RBIPN62022062832123456</StsId>
                                                 <OrgnlEndToEndId>/XUTR/HDFCH22194004232</OrgnlEndToEndId>
                                                 <OrgnlTxId>HDFCN52022062824954013</OrgnlTxId>
                                                 <TxSts>RJCT</TxSts>
                                                 <StsRsnInf>
                                                     <Rsn>
                                                         <Cd>RJ10</Cd>
                                                     </Rsn>
                                                     <AddtlInf>BatchId:0007</AddtlInf>
                                                 </StsRsnInf>
                                             </TxInfAndSts>
                                             <TxInfAndSts>
                                                 <StsId>RBIPN62022062832123457</StsId>
                                                 <OrgnlEndToEndId>/XUTR/HDFCH22194004233</OrgnlEndToEndId>
                                                 <OrgnlTxId>HDFCN52022062824954014</OrgnlTxId>
                                                 <TxSts>RJCT</TxSts>
                                                 <StsRsnInf>
                                                     <Rsn>
                                                         <Cd>RJ10</Cd>
                                                     </Rsn>
                                                     <AddtlInf>BatchId:0007</AddtlInf>
                                                 </StsRsnInf>
                                             </TxInfAndSts>
                                         </FIToFIPmtStsRpt>
                                     </Document>
                                 </RequestPayload>
                
                """;

        // Act
        pacs002XmlProcessor.processXML(validXml);

        // Assert: Capture the arguments passed to KafkaUtils
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaUtils, times(2)).publishToResponseTopic(payloadCaptor.capture(), topicCaptor.capture());

        String capturedPayload = payloadCaptor.getValue();
        String capturedTopic = topicCaptor.getValue();

        // Verify the topic is correct
        assertEquals("msg-tracker-topic", capturedTopic);

        // Optionally, deserialize the payload and check key fields
        var jsonNode = objectMapper.readTree(capturedPayload);
        System.out.println(jsonNode);

        assertEquals("NIL", jsonNode.get("header").get("source").asText());
        assertEquals("2", jsonNode.get("header").get("orignlReqCount").asText());
        assertEquals("Inward", jsonNode.get("header").get("flowType").asText());
        assertTrue(jsonNode.get("body").has("reqPayload"));

        // Basic check: reqPayload should contain the XML
        String reqPayload = jsonNode.get("body").get("reqPayload").asText();
        assertTrue(reqPayload.contains("HDFCN52022062824954013"));
        assertTrue(reqPayload.contains("HDFCN52022062824954014"));
    }

    @Test
    void testProcessXML_withValidXmlEPH_shouldPublishToKafka() throws Exception {
        // Arrange: Your sample pacs.002 XML
        String validXml = """
                <RequestPayload xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.09">
                                     <AppHdr>
                                         <BizMsgIdr>RBIP202204016000000001</BizMsgIdr>
                                     </AppHdr>
                                     <Document>
                                         <FIToFIPmtStsRpt>
                                             <GrpHdr>
                                                 <MsgId>RBIP202204016000000001</MsgId>
                                             </GrpHdr>
                                             <TxInfAndSts>
                                                 <StsId>RBIPN62022062832123456</StsId>
                                                 <OrgnlEndToEndId>/XUTR/HDFCH22194004232</OrgnlEndToEndId>
                                                 <OrgnlTxId>HDFCN52022062894954013</OrgnlTxId>
                                                 <TxSts>RJCT</TxSts>
                                                 <StsRsnInf>
                                                     <Rsn>
                                                         <Cd>RJ10</Cd>
                                                     </Rsn>
                                                     <AddtlInf>BatchId:0007</AddtlInf>
                                                 </StsRsnInf>
                                             </TxInfAndSts>
                                             <TxInfAndSts>
                                                 <StsId>RBIPN62022062832123457</StsId>
                                                 <OrgnlEndToEndId>/XUTR/HDFCH22194004233</OrgnlEndToEndId>
                                                 <OrgnlTxId>HDFCN52022062894954014</OrgnlTxId>
                                                 <TxSts>RJCT</TxSts>
                                                 <StsRsnInf>
                                                     <Rsn>
                                                         <Cd>RJ10</Cd>
                                                     </Rsn>
                                                     <AddtlInf>BatchId:0007</AddtlInf>
                                                 </StsRsnInf>
                                             </TxInfAndSts>
                                         </FIToFIPmtStsRpt>
                                     </Document>
                                 </RequestPayload>
                
                """;

        // Act
        pacs002XmlProcessor.processXML(validXml);

        // Assert: Capture the arguments passed to KafkaUtils
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaUtils, times(2)).publishToResponseTopic(payloadCaptor.capture(), topicCaptor.capture());

        String capturedPayload = payloadCaptor.getValue();
        String capturedTopic = topicCaptor.getValue();

        // Verify the topic is correct
        assertEquals("msg-tracker-topic", capturedTopic);

        // Optionally, deserialize the payload and check key fields
        var jsonNode = objectMapper.readTree(capturedPayload);
        System.out.println(jsonNode);

        assertEquals("NIL", jsonNode.get("header").get("source").asText());
        assertEquals("2", jsonNode.get("header").get("orignlReqCount").asText());
        assertEquals("Inward", jsonNode.get("header").get("flowType").asText());
        assertTrue(jsonNode.get("body").has("reqPayload"));

        // Basic check: reqPayload should contain the XML
        String reqPayload = jsonNode.get("body").get("reqPayload").asText();
        assertTrue(reqPayload.contains("HDFCN52022062894954013"));
        assertTrue(reqPayload.contains("HDFCN52022062894954014"));
    }

    @Test
    void testProcessXML_withValidXmlFCEPH_shouldPublishToKafka() throws Exception {
        // Arrange: Your sample pacs.002 XML
        String validXml = """
                <RequestPayload xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.09">
                                     <AppHdr>
                                         <BizMsgIdr>RBIP202204016000000001</BizMsgIdr>
                                     </AppHdr>
                                     <Document>
                                         <FIToFIPmtStsRpt>
                                             <GrpHdr>
                                                 <MsgId>RBIP202204016000000001</MsgId>
                                             </GrpHdr>
                                             <TxInfAndSts>
                                                 <StsId>RBIPN62022062832123456</StsId>
                                                 <OrgnlEndToEndId>/XUTR/HDFCH22194004232</OrgnlEndToEndId>
                                                 <OrgnlTxId>HDFCN52022062824954013</OrgnlTxId>
                                                 <TxSts>RJCT</TxSts>
                                                 <StsRsnInf>
                                                     <Rsn>
                                                         <Cd>RJ10</Cd>
                                                     </Rsn>
                                                     <AddtlInf>BatchId:0007</AddtlInf>
                                                 </StsRsnInf>
                                             </TxInfAndSts>
                                             <TxInfAndSts>
                                                 <StsId>RBIPN62022062832123457</StsId>
                                                 <OrgnlEndToEndId>/XUTR/HDFCH22194004233</OrgnlEndToEndId>
                                                 <OrgnlTxId>HDFCN52022062894954014</OrgnlTxId>
                                                 <TxSts>RJCT</TxSts>
                                                 <StsRsnInf>
                                                     <Rsn>
                                                         <Cd>RJ10</Cd>
                                                     </Rsn>
                                                     <AddtlInf>BatchId:0007</AddtlInf>
                                                 </StsRsnInf>
                                             </TxInfAndSts>
                                         </FIToFIPmtStsRpt>
                                     </Document>
                                 </RequestPayload>
                
                """;

        // Act
        pacs002XmlProcessor.processXML(validXml);

        // Assert: Capture the arguments passed to KafkaUtils
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaUtils, times(3)).publishToResponseTopic(payloadCaptor.capture(), topicCaptor.capture());

        String capturedPayload = payloadCaptor.getValue();
        String capturedTopic = topicCaptor.getValue();

        // Verify the topic is correct
        assertEquals("msg-tracker-topic", capturedTopic);

        // Optionally, deserialize the payload and check key fields
        var jsonNode = objectMapper.readTree(capturedPayload);
        System.out.println(jsonNode);

        assertEquals("NIL", jsonNode.get("header").get("source").asText());
        assertEquals("2", jsonNode.get("header").get("orignlReqCount").asText());
        assertEquals("Inward", jsonNode.get("header").get("flowType").asText());
        assertTrue(jsonNode.get("body").has("reqPayload"));

        // Basic check: reqPayload should contain the XML
        String reqPayload = jsonNode.get("body").get("reqPayload").asText();
        assertTrue(reqPayload.contains("HDFCN52022062824954013"));
        assertTrue(reqPayload.contains("HDFCN52022062894954014"));
    }


    @Test
    void testProcessXML_withDbLookup_shouldHandleUnknownDigit() throws Exception {
        // Mock DAO
        when(dao.findTargetByTxnId("BION52022062824954013")).thenReturn("FC");

        // Mock utility methods
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("BIZMSGIDR123");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("pacs.002.001.09");


        pacs002XmlProcessor.processXML(sampleXml);

        verify(dao, times(2)).findTargetByTxnId("BION52022062824954013");
        verify(kafkaUtils, atLeastOnce()).publishToResponseTopic(anyString(), eq("fc-topic"));
    }

    @Test
    void testFilterTxInfAndSts_shouldReturnFilteredDocument() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(sampleXml.getBytes()));

        when(dao.findTargetByTxnId(anyString())).thenReturn("FC"); // Mock DB fallback

        Document filteredDoc = pacs002XmlProcessor.filterTxInfAndSts(doc, 0, 4);

        assertNotNull(filteredDoc);
        String resultXml = pacs002XmlProcessor.documentToXml(filteredDoc);
        System.out.println(resultXml);

        // Basic checks
        assert resultXml.contains("FIToFIPmtStsRpt");
    }

    @Test
    void testDocumentToXml_shouldConvertDocument() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(sampleXml.getBytes()));

        String xml = pacs002XmlProcessor.documentToXml(doc);
        assert xml.contains("RequestPayload");
    }

    @Test
    void testExtractOrgnlItmIdDigit_shouldExtractCorrectDigit() {
        int digit = Pacs002XmlProcessor.extractOrgnlItmIdDigit("HDFCN00000000999"); // length 17, 15th char '9'
        assert digit == 9;

        int invalid = Pacs002XmlProcessor.extractOrgnlItmIdDigit("INVALID"); // too short
        assert invalid == -1;

    }


    @Test
    void testProcessXML_shouldHandleException() {
        // Pass malformed XML
        String badXml = "<bad";

        pacs002XmlProcessor.processXML(badXml);

        // Should not throw exception, but log error
        verifyNoInteractions(kafkaUtils);
    }
}

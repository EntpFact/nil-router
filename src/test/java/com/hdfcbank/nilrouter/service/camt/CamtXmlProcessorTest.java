package com.hdfcbank.nilrouter.service.camt;


import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.service.AuditService;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.xml.xpath.XPathExpressionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CamtXmlProcessorTest {

    @InjectMocks
    private CamtXmlProcessor camtXmlProcessor;

    @Mock
    private KafkaUtils kafkaUtils;

    @Mock
    private UtilityMethods utilityMethods;

    @Mock
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(camtXmlProcessor, "fcTopic", "fc-topic");
        ReflectionTestUtils.setField(camtXmlProcessor, "ephTopic", "eph-topic");
        ReflectionTestUtils.setField(camtXmlProcessor, "msgEventTrackerTopic", "event-tracker-topic");
    }

    private String getSampleXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                                          <RequestPayload>
                                              <AppHdr xmlns:xsi="urn:iso:std:iso:20022:tech:xsd:Header" xmlns:sig="http://www.w3.org/2000/09/xmldsig#" xmlns="urn:iso:std:iso:20022:tech:xsd:head.001.001.02">
                                             <BizMsgIdr>RBIP202204016000000001</BizMsgIdr>
                                                  <MsgDefIdr>camt.054.001.08</MsgDefIdr>
                                                  <BizSvc>NEFTBankDebitCreditNotification</BizSvc>
                                                  <CreDt>2022-04-03T13:45:01Z</CreDt>
                                          </AppHdr>
                                          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.054.001.08">
                                             <BkToCstmrDbtCdtNtfctn>
                                                <GrpHdr>
                                                   <MsgId>RBIP202204016000000001</MsgId>
                                                   <CreDtTm>2022-04-03T13:45:01</CreDtTm>
                                          		 <AddtInf>BatchId:0007</AddtInf>
                                                </GrpHdr>
                                                <Ntfctn>
                                                   <Ntry>
                                                      <Sts>
                                                         <Cd>E0B</Cd>
                                                      </Sts>
                                                      <ValDt>
                                                         <Dt>2022-04-03</Dt>
                                                      </ValDt>
                                                      <NtryDtls>
                                                         <TxDtls>
                                          			      <Amt Ccy="INR">1000.00</Amt>
                                          				  <CdtDbtInd>DBIT</CdtDbtInd>
                                                            <RmtInf>
                                                               <Ustrd>1</Ustrd>
                                                            </RmtInf>
                                                         </TxDtls>
                                          			    <TxDtls>
                                          			      <Amt Ccy="INR">0.00</Amt>
                                          			      <CdtDbtInd>RJTDBIT</CdtDbtInd>
                                                            <RmtInf>
                                                               <Ustrd>0</Ustrd>
                                                            </RmtInf>
                                                         </TxDtls>
                                                      </NtryDtls>
                                                   </Ntry>
                                                </Ntfctn>
                                             </BkToCstmrDbtCdtNtfctn>
                                          </Document>
                                          </RequestPayload>""";
    }

    @Test
    void parseXml_validXml_shouldPublishJsonAndXml() throws Exception {
        String xml = getSampleXml();
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("MSG1234567890123456");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("camt.052.001.02");

        assertDoesNotThrow(() -> camtXmlProcessor.parseXml(xml));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaUtils).publishToResponseTopic(jsonCaptor.capture(), eq("event-tracker-topic"));
        String publishedJson = jsonCaptor.getValue();
        assertNotNull(publishedJson);
        assertTrue(publishedJson.contains("MSG1234567890123456"));
        assertTrue(publishedJson.contains("camt.052.001.02"));

        verify(kafkaUtils).publishToResponseTopic(xml, "fc-topic");
        verify(kafkaUtils).publishToResponseTopic(xml, "eph-topic");
    }

    @Test
    void parseXml_invalidXml_shouldThrowRuntimeException() {
        String invalidXml = "<RequestPayload><AppHdr><BizMsgIdr>123"; // Malformed XML

        RuntimeException exception = assertThrows(RuntimeException.class, () -> camtXmlProcessor.parseXml(invalidXml));
        assertTrue(exception.getCause() instanceof org.xml.sax.SAXParseException);

        verifyNoInteractions(kafkaUtils);
    }

    @Test
    void parseXml_utilityMethodThrows_shouldThrowRuntimeException() throws Exception {
        String xml = getSampleXml();
        when(utilityMethods.getBizMsgIdr(any())).thenThrow(new XPathExpressionException("Error in XPath"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> camtXmlProcessor.parseXml(xml));
        assertTrue(exception.getCause() instanceof XPathExpressionException);

        verifyNoInteractions(kafkaUtils);
    }

    @Test
    void parseXml_shouldPublishToAllKafkaTopics() throws Exception {
        String xml = getSampleXml();
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("RBIP202204016000000001");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("camt.054.001.08");

        camtXmlProcessor.parseXml(xml);

        verify(kafkaUtils).publishToResponseTopic(contains("RBIP202204016000000001"), eq("event-tracker-topic"));
        verify(kafkaUtils).publishToResponseTopic(eq(xml), eq("fc-topic"));
        verify(kafkaUtils).publishToResponseTopic(eq(xml), eq("eph-topic"));
    }

    @Test
    void parseXml_emptyXml_shouldThrowException() {
        String emptyXml = "";

        assertThrows(RuntimeException.class, () -> camtXmlProcessor.parseXml(emptyXml));

        verifyNoInteractions(kafkaUtils);
    }
}

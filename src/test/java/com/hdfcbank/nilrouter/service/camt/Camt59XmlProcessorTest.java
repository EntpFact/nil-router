package com.hdfcbank.nilrouter.service.camt;


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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class Camt59XmlProcessorTest {

    @InjectMocks
    private Camt59XmlProcessor camt59XmlProcessor;

    @Mock
    private UtilityMethods utilityMethods;

    @Mock
    private KafkaUtils kafkaUtils;

    @Mock
    private NilRepository dao;

    @Mock
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(camt59XmlProcessor, "sfmsTopic", "sfms-topic");
        ReflectionTestUtils.setField(camt59XmlProcessor, "fcTopic", "fc-topic");
        ReflectionTestUtils.setField(camt59XmlProcessor, "ephTopic", "eph-topic");
        ReflectionTestUtils.setField(camt59XmlProcessor, "msgEventTrackerTopic", "event-tracker-topic");
    }

    @Test
    void testProcessXML_outwardFlow_shouldPublishToKafka() throws Exception {
        String sampleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<RequestPayload>\n" +
                "<AppHdr xmlns=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.02\"  xmlns:sig=\"http://www.w3.org/2000/09/xmldsig#\" xmlns:xsi=\"urn:iso:std:iso:20022:tech:xsd:Header\" >\n" +
                " <Fr>\n" +
                "        <FIId>\n" +
                "            <FinInstnId>\n" +
                "                <ClrSysMmbId>\n" +
                "                    <MmbId>HDFC0000001</MmbId>\n" +
                "                </ClrSysMmbId>\n" +
                "            </FinInstnId>\n" +
                "        </FIId>\n" +
                "    </Fr>\n" +
                "    <To>\n" +
                "        <FIId>\n" +
                "            <FinInstnId>\n" +
                "                <ClrSysMmbId>\n" +
                "                    <MmbId>RBIP0NEFTSC</MmbId>\n" +
                "                </ClrSysMmbId>\n" +
                "            </FinInstnId>\n" +
                "        </FIId>\n" +
                "    </To>\n" +
                "    <BizMsgIdr>RBIP200608226200200080</BizMsgIdr>\n" +
                "    <MsgDefIdr>camt.059.001.06</MsgDefIdr>\n" +
                "    <BizSvc>NEFTCustomerCreditNotification</BizSvc>\n" +
                "    <CreDt>2006-08-22T12:12:00Z</CreDt>\n" +
                "\t \n" +
                "</AppHdr>\n" +
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.059.001.06\">\n" +
                "    <NtfctnToRcvStsRpt>\n" +
                "        <GrpHdr>\n" +
                "            <MsgId>RBIP200608226200200080</MsgId>\n" +
                "            <CreDtTm>2006-08-22T12:12:00</CreDtTm>\n" +
                "        </GrpHdr>\n" +
                "        <OrgnlNtfctnAndSts>\n" +
                "            <OrgnlNtfctnRef>\n" +
                "                <DbtrAgt>\n" +
                "                    <FinInstnId>\n" +
                "                        <ClrSysMmbId>\n" +
                "                            <MmbId>HDFC00650122</MmbId>\n" +
                "                        </ClrSysMmbId>\n" +
                "                    </FinInstnId>\n" +
                "                </DbtrAgt>\n" +
                "                <OrgnlItmAndSts>\n" +
                "                    <OrgnlItmId>HDFCN52022062824954013</OrgnlItmId>\n" +
                "                    <OrgnlEndToEndId>/XUTR/HDFCH82194004241</OrgnlEndToEndId>\n" +
                "                    <Amt Ccy=\"INR\">100.00</Amt>\n" +
                "                    <XpctdValDt>2006-12-07</XpctdValDt>\n" +
                "                    <ItmSts>RCVD</ItmSts>\n" +
                "                </OrgnlItmAndSts>\n" +
                "            </OrgnlNtfctnRef>\n" +
                "            <OrgnlNtfctnRef>\n" +
                "                <DbtrAgt>\n" +
                "                    <FinInstnId>\n" +
                "                        <ClrSysMmbId>\n" +
                "                            <MmbId>HDFC0065012</MmbId>\n" +
                "                        </ClrSysMmbId>\n" +
                "                    </FinInstnId>\n" +
                "                </DbtrAgt>\n" +
                "                <OrgnlItmAndSts>\n" +
                "                    <OrgnlItmId>HDFCN52022062824954014</OrgnlItmId>\n" +
                "                    <OrgnlEndToEndId>/XUTR/HDFCH82194004241</OrgnlEndToEndId>\n" +
                "                    <Amt Ccy=\"INR\">100.00</Amt>\n" +
                "                    <XpctdValDt>2006-12-07</XpctdValDt>\n" +
                "                    <ItmSts>RCVD</ItmSts>\n" +
                "                </OrgnlItmAndSts>\n" +
                "            </OrgnlNtfctnRef>\n" +
                "\t\t</OrgnlNtfctnAndSts>\n" +
                "    </NtfctnToRcvStsRpt>\n" +
                "</Document>\n" +
                "</RequestPayload>\n";

        when(utilityMethods.isOutward(sampleXml)).thenReturn(true);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("BIZMSGIDR123");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("camt.059.001.03");

        camt59XmlProcessor.processXML(sampleXml);

        // Verify JSON and XML published to Kafka
        verify(kafkaUtils, times(1)).publishToResponseTopic(contains("BIZMSGIDR123"), eq("event-tracker-topic"));
        verify(kafkaUtils, times(1)).publishToResponseTopic(eq(sampleXml), eq("sfms-topic"));
    }

    @Test
    void testProcessXML_inwardFlow_FC() throws Exception {
        String sampleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<RequestPayload>\n" +
                "<AppHdr xmlns=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.02\"  xmlns:sig=\"http://www.w3.org/2000/09/xmldsig#\" xmlns:xsi=\"urn:iso:std:iso:20022:tech:xsd:Header\" >\n" +
                " <Fr>\n" +
                "        <FIId>\n" +
                "            <FinInstnId>\n" +
                "                <ClrSysMmbId>\n" +
                "                    <MmbId>RBIP0NEFTSC</MmbId>\n" +
                "                </ClrSysMmbId>\n" +
                "            </FinInstnId>\n" +
                "        </FIId>\n" +
                "    </Fr>\n" +
                "    <To>\n" +
                "        <FIId>\n" +
                "            <FinInstnId>\n" +
                "                <ClrSysMmbId>\n" +
                "                    <MmbId>HDFC0000001</MmbId>\n" +
                "                </ClrSysMmbId>\n" +
                "            </FinInstnId>\n" +
                "        </FIId>\n" +
                "    </To>\n" +
                "    <BizMsgIdr>RBIP200608226200200080</BizMsgIdr>\n" +
                "    <MsgDefIdr>camt.059.001.06</MsgDefIdr>\n" +
                "    <BizSvc>NEFTCustomerCreditNotification</BizSvc>\n" +
                "    <CreDt>2006-08-22T12:12:00Z</CreDt>\n" +
                "\t \n" +
                "</AppHdr>\n" +
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.059.001.06\">\n" +
                "    <NtfctnToRcvStsRpt>\n" +
                "        <GrpHdr>\n" +
                "            <MsgId>RBIP200608226200200080</MsgId>\n" +
                "            <CreDtTm>2006-08-22T12:12:00</CreDtTm>\n" +
                "        </GrpHdr>\n" +
                "        <OrgnlNtfctnAndSts>\n" +
                "            <OrgnlNtfctnRef>\n" +
                "                <DbtrAgt>\n" +
                "                    <FinInstnId>\n" +
                "                        <ClrSysMmbId>\n" +
                "                            <MmbId>HDFC00650122</MmbId>\n" +
                "                        </ClrSysMmbId>\n" +
                "                    </FinInstnId>\n" +
                "                </DbtrAgt>\n" +
                "                <OrgnlItmAndSts>\n" +
                "                    <OrgnlItmId>HDFCN52022062824954013</OrgnlItmId>\n" +
                "                    <OrgnlEndToEndId>/XUTR/HDFCH82194004241</OrgnlEndToEndId>\n" +
                "                    <Amt Ccy=\"INR\">100.00</Amt>\n" +
                "                    <XpctdValDt>2006-12-07</XpctdValDt>\n" +
                "                    <ItmSts>RCVD</ItmSts>\n" +
                "                </OrgnlItmAndSts>\n" +
                "            </OrgnlNtfctnRef>\n" +
                "            <OrgnlNtfctnRef>\n" +
                "                <DbtrAgt>\n" +
                "                    <FinInstnId>\n" +
                "                        <ClrSysMmbId>\n" +
                "                            <MmbId>HDFC0065012</MmbId>\n" +
                "                        </ClrSysMmbId>\n" +
                "                    </FinInstnId>\n" +
                "                </DbtrAgt>\n" +
                "                <OrgnlItmAndSts>\n" +
                "                    <OrgnlItmId>HDFCN52022062824954014</OrgnlItmId>\n" +
                "                    <OrgnlEndToEndId>/XUTR/HDFCH82194004241</OrgnlEndToEndId>\n" +
                "                    <Amt Ccy=\"INR\">100.00</Amt>\n" +
                "                    <XpctdValDt>2006-12-07</XpctdValDt>\n" +
                "                    <ItmSts>RCVD</ItmSts>\n" +
                "                </OrgnlItmAndSts>\n" +
                "            </OrgnlNtfctnRef>\n" +
                "\t\t</OrgnlNtfctnAndSts>\n" +
                "    </NtfctnToRcvStsRpt>\n" +
                "</Document>\n" +
                "</RequestPayload>\n";

        when(utilityMethods.isOutward(sampleXml)).thenReturn(false);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("BIZMSGIDR123");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("camt.059.001.03");

        camt59XmlProcessor.processXML(sampleXml);

        // Verify filtered XML for FC published
        verify(kafkaUtils, times(1)).publishToResponseTopic(contains("HDFCN52022062824954013"), eq("fc-topic"));

        // Verify JSON published to event tracker
        verify(kafkaUtils, times(1)).publishToResponseTopic(contains("RBIP200608226200200080"), eq("event-tracker-topic"));
    }

    @Test
    void testProcessXML_inwardFlow_EPH() throws Exception {
        String sampleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<RequestPayload>\n" +
                "<AppHdr xmlns=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.02\"  xmlns:sig=\"http://www.w3.org/2000/09/xmldsig#\" xmlns:xsi=\"urn:iso:std:iso:20022:tech:xsd:Header\" >\n" +
                " <Fr>\n" +
                "        <FIId>\n" +
                "            <FinInstnId>\n" +
                "                <ClrSysMmbId>\n" +
                "                    <MmbId>RBIP0NEFTSC</MmbId>\n" +
                "                </ClrSysMmbId>\n" +
                "            </FinInstnId>\n" +
                "        </FIId>\n" +
                "    </Fr>\n" +
                "    <To>\n" +
                "        <FIId>\n" +
                "            <FinInstnId>\n" +
                "                <ClrSysMmbId>\n" +
                "                    <MmbId>HDFC0000001</MmbId>\n" +
                "                </ClrSysMmbId>\n" +
                "            </FinInstnId>\n" +
                "        </FIId>\n" +
                "    </To>\n" +
                "    <BizMsgIdr>RBIP200608226200200080</BizMsgIdr>\n" +
                "    <MsgDefIdr>camt.059.001.06</MsgDefIdr>\n" +
                "    <BizSvc>NEFTCustomerCreditNotification</BizSvc>\n" +
                "    <CreDt>2006-08-22T12:12:00Z</CreDt>\n" +
                "\t \n" +
                "</AppHdr>\n" +
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.059.001.06\">\n" +
                "    <NtfctnToRcvStsRpt>\n" +
                "        <GrpHdr>\n" +
                "            <MsgId>RBIP200608226200200080</MsgId>\n" +
                "            <CreDtTm>2006-08-22T12:12:00</CreDtTm>\n" +
                "        </GrpHdr>\n" +
                "        <OrgnlNtfctnAndSts>\n" +
                "            <OrgnlNtfctnRef>\n" +
                "                <DbtrAgt>\n" +
                "                    <FinInstnId>\n" +
                "                        <ClrSysMmbId>\n" +
                "                            <MmbId>HDFC00650122</MmbId>\n" +
                "                        </ClrSysMmbId>\n" +
                "                    </FinInstnId>\n" +
                "                </DbtrAgt>\n" +
                "                <OrgnlItmAndSts>\n" +
                "                    <OrgnlItmId>HDFCN52022062894954013</OrgnlItmId>\n" +
                "                    <OrgnlEndToEndId>/XUTR/HDFCH82194004241</OrgnlEndToEndId>\n" +
                "                    <Amt Ccy=\"INR\">100.00</Amt>\n" +
                "                    <XpctdValDt>2006-12-07</XpctdValDt>\n" +
                "                    <ItmSts>RCVD</ItmSts>\n" +
                "                </OrgnlItmAndSts>\n" +
                "            </OrgnlNtfctnRef>\n" +
                "            <OrgnlNtfctnRef>\n" +
                "                <DbtrAgt>\n" +
                "                    <FinInstnId>\n" +
                "                        <ClrSysMmbId>\n" +
                "                            <MmbId>HDFC0065012</MmbId>\n" +
                "                        </ClrSysMmbId>\n" +
                "                    </FinInstnId>\n" +
                "                </DbtrAgt>\n" +
                "                <OrgnlItmAndSts>\n" +
                "                    <OrgnlItmId>HDFCN52022062894954014</OrgnlItmId>\n" +
                "                    <OrgnlEndToEndId>/XUTR/HDFCH82194004241</OrgnlEndToEndId>\n" +
                "                    <Amt Ccy=\"INR\">100.00</Amt>\n" +
                "                    <XpctdValDt>2006-12-07</XpctdValDt>\n" +
                "                    <ItmSts>RCVD</ItmSts>\n" +
                "                </OrgnlItmAndSts>\n" +
                "            </OrgnlNtfctnRef>\n" +
                "\t\t</OrgnlNtfctnAndSts>\n" +
                "    </NtfctnToRcvStsRpt>\n" +
                "</Document>\n" +
                "</RequestPayload>\n";

        when(utilityMethods.isOutward(sampleXml)).thenReturn(false);
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("RBIP200608226200200080");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("camt.059.001.03");

        camt59XmlProcessor.processXML(sampleXml);

        // Verify filtered XML for EPH published
        verify(kafkaUtils, times(1)).publishToResponseTopic(contains("HDFCN52022062894954014"), eq("eph-topic"));

        // Verify JSON published to event tracker
        verify(kafkaUtils, times(1)).publishToResponseTopic(contains("RBIP200608226200200080"), eq("event-tracker-topic"));
    }

    @Test
    void testProcessXML_exceptionHandling_shouldNotThrow() throws Exception {
        String invalidXml = "<RequestPayload><Invalid></Invalid>"; // malformed XML

        when(utilityMethods.isOutward(invalidXml)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> camt59XmlProcessor.processXML(invalidXml));

        // Verify no Kafka calls (since it will fail before Kafka publish)
        verify(kafkaUtils, never()).publishToResponseTopic(any(), any());
    }
}
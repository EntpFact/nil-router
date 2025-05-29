package com.hdfcbank.nilrouter.service.pacs008;

import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.service.AuditService;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class Pacs008XmlProcessorTest {

    @InjectMocks
    private Pacs008XmlProcessor processor;

    @Mock
    private InwardService inwardService;
    @Mock
    private AuditService auditService;
    @Mock
    private KafkaUtils kafkaUtils;
    @Mock
    private CugApproach cugApproach;
    @Mock
    private UtilityMethods utilityMethods;

    @Test
    void testParseXml_Outward() throws Exception {
        String xml = "<test>outward</test>";
        when(utilityMethods.isOutward(xml)).thenReturn(true);
        processor.parseXml(xml);
        verify(auditService).constructOutwardJsonAndPublish(xml);
        verifyNoInteractions(cugApproach, inwardService);
    }

    @Test
    void testParseXml_CugApproach() throws Exception {
        String xml = "<test>cug</test>";
        when(utilityMethods.isOutward(xml)).thenReturn(false);
        ReflectionTestUtils.setField(processor, "cugFlag", "true");
        processor.parseXml(xml);
        verify(cugApproach).processCugApproach(xml);
        verifyNoInteractions(inwardService);
    }

    @Test
    void testParseXml_LateReturn() throws Exception {
        String xml = "<test>lateReturn</test>";
        when(utilityMethods.isOutward(xml)).thenReturn(false);
        ReflectionTestUtils.setField(processor, "cugFlag", "false");
        Pacs008XmlProcessor spyProcessor = spy(processor);
        doReturn(true).when(spyProcessor).containsReturnTags(xml);
        spyProcessor.parseXml(xml);
        verify(inwardService).processLateReturn(xml);
        verify(inwardService, never()).processFreshInward(any());
    }

    @Test
    void testParseXml_FreshInward() throws Exception {
        String xml = "<test>freshInward</test>";
        when(utilityMethods.isOutward(xml)).thenReturn(false);
        ReflectionTestUtils.setField(processor, "cugFlag", "false");
        Pacs008XmlProcessor spyProcessor = spy(processor);
        doReturn(false).when(spyProcessor).containsReturnTags(xml);
        spyProcessor.parseXml(xml);
        verify(inwardService).processFreshInward(xml);
        verify(inwardService, never()).processLateReturn(any());
    }

    @Test
    void testContainsReturnTags_TrueInstrInf() throws Exception {
        String xml = """
                <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">
                  <CdtTrfTxInf>
                    <InstrForCdtrAgt><InstrInf>HDFCN12345678901234567</InstrInf></InstrForCdtrAgt>
                  </CdtTrfTxInf>
                </Document>
                """;
        boolean result = processor.containsReturnTags(xml);
        assert (result);
    }

    @Test
    void testContainsReturnTags_TrueUstrd() throws Exception {
        String xml = """
                <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">
                  <CdtTrfTxInf>
                    <RmtInf><Ustrd>HDFCN12345678901234567</Ustrd></RmtInf>
                  </CdtTrfTxInf>
                </Document>
                """;
        boolean result = processor.containsReturnTags(xml);
        assert (result);
    }

    @Test
    void testContainsReturnTags_False() throws Exception {
        String xml = """
                <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.09\">
                  <CdtTrfTxInf>
                    <InstrForCdtrAgt><InstrInf>NOIDHERE</InstrInf></InstrForCdtrAgt>
                    <RmtInf><Ustrd>NOIDHERE</Ustrd></RmtInf>
                  </CdtTrfTxInf>
                </Document>
                """;
        boolean result = processor.containsReturnTags(xml);
        assert (!result);
    }

    @Test
    void testContainsValidId_True() {
        String valid = "HDFCN12345678901234567";
        boolean result = ReflectionTestUtils.invokeMethod(processor, "containsValidId", valid);
        assert (result);
    }

    @Test
    void testContainsValidId_False() {
        String invalid = "HDFCN1234567890";
        boolean result = ReflectionTestUtils.invokeMethod(processor, "containsValidId", invalid);
        assert (!result);
    }
}



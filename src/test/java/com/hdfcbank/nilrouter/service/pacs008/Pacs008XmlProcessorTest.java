package com.hdfcbank.nilrouter.service.pacs008;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class Pacs008XmlProcessorTest {
    @Mock
    private InwardService inwardService;
    @Mock
    private UtilityMethods utilityMethods;
    @Mock
    private KafkaUtils kafkaUtils;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private Pacs008XmlProcessor processor;

    private final String outwardXml = "<Document><CdtTrfTxInf></CdtTrfTxInf></Document>";
    private final String inwardXml = "<Document><CdtTrfTxInf><RmtInf><Ustrd>HDFCN52022062824954015</Ustrd></RmtInf></CdtTrfTxInf></Document>";
    private final String inwardXmlNoReturn = "<Document><CdtTrfTxInf><RmtInf><Ustrd>NOID</Ustrd></RmtInf></CdtTrfTxInf></Document>";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(processor, "sfmsTopic", "sfms-topic");
        ReflectionTestUtils.setField(processor, "msgEventTrackerTopic", "msg-topic");
        ReflectionTestUtils.setField(processor, "cugFlag", "false");
    }

    @Test
    void testParseXml_Outward() throws Exception {
        when(utilityMethods.isOutward(anyString())).thenReturn(true);
        doNothing().when(kafkaUtils).publishToResponseTopic(anyString(), anyString());
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        processor.parseXml(outwardXml);
        verify(kafkaUtils, times(2)).publishToResponseTopic(anyString(), anyString());
    }

    @Test
    void testParseXml_Inward_CugTrue() throws Exception {
        ReflectionTestUtils.setField(processor, "cugFlag", "true");
        when(utilityMethods.isOutward(anyString())).thenReturn(false);
        doNothing().when(inwardService).processCugApproach(anyString());
        processor.parseXml(inwardXml);
        verify(inwardService).processCugApproach(anyString());
    }

    @Test
    void testParseXml_Inward_LateReturn() throws Exception {
        when(utilityMethods.isOutward(anyString())).thenReturn(false);
        ReflectionTestUtils.setField(processor, "cugFlag", "false");
        Pacs008XmlProcessor spyProcessor = spy(processor);
        doReturn(true).when(spyProcessor).containsReturnTags(anyString());
        doNothing().when(inwardService).processLateReturn(anyString());
        spyProcessor.parseXml(inwardXml);
        verify(inwardService).processLateReturn(anyString());
    }

    @Test
    void testParseXml_Inward_FreshInward() throws Exception {
        when(utilityMethods.isOutward(anyString())).thenReturn(false);
        ReflectionTestUtils.setField(processor, "cugFlag", "false");
        Pacs008XmlProcessor spyProcessor = spy(processor);
        doReturn(false).when(spyProcessor).containsReturnTags(anyString());
        doNothing().when(inwardService).processFreshInward(anyString());
        spyProcessor.parseXml(inwardXmlNoReturn);
        verify(inwardService).processFreshInward(anyString());
    }

    @Test
    void testConstructOutwardJsonAndPublish() throws Exception {
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("msgid");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("msgtype");
        when(utilityMethods.getTotalAmount(any())).thenReturn(BigDecimal.valueOf(1000));
        doNothing().when(kafkaUtils).publishToResponseTopic(anyString(), anyString());
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(new ObjectMapper().writerWithDefaultPrettyPrinter());
        processor.constructOutwardJsonAndPublish(outwardXml);
        verify(kafkaUtils, times(2)).publishToResponseTopic(anyString(), anyString());
    }

    @Test
    void testContainsReturnTags_True() throws Exception {
        boolean result = processor.containsReturnTags(inwardXml);
        assertTrue(result);
    }

    @Test
    void testContainsReturnTags_False() throws Exception {
        boolean result = processor.containsReturnTags(inwardXmlNoReturn);
        assertFalse(result);
    }

    @Test
    void testContainsValidId_True() {
        boolean result = ReflectionTestUtils.invokeMethod(processor, "containsValidId", "HDFCN52022062824954015");
        assertTrue(result);
    }

    @Test
    void testContainsValidId_False() {
        boolean result = ReflectionTestUtils.invokeMethod(processor, "containsValidId", "NOID");
        assertFalse(result);
    }
}



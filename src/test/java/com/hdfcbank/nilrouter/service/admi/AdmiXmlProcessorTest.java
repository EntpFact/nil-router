package com.hdfcbank.nilrouter.service.admi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdmiXmlProcessorTest {

    @Mock
    UtilityMethods utilityMethods;
    @Mock
    NilRepository nilRepository;
    @Mock
    KafkaUtils kafkaUtils;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    ObjectWriter objectWriter;

    @InjectMocks
    AdmiXmlProcessor admiXmlProcessor;

    private final String msgEventTrackerTopic = "test-topic";

    @BeforeEach
    void setUp() throws JsonProcessingException {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(admiXmlProcessor, "msgEventTrackerTopic", msgEventTrackerTopic);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(objectWriter.writeValueAsString(any())).thenAnswer(invocation -> "{}");
    }

    private String getXml(String evtCd) {
        return "<RequestPayload><AppHdr><CreDt>2024-06-06</CreDt></AppHdr>" +
                "<Document><EvtCd>" + evtCd + "</EvtCd></Document></RequestPayload>";
    }

    @Test
    void testParseXml_evtCdF95() throws Exception {
        String xml = getXml("F95");
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("HDFC202501135000288850");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("MSGDEFIDR");
        doNothing().when(kafkaUtils).publishToResponseTopic(any(), any());

        admiXmlProcessor.parseXml(xml);
        verify(kafkaUtils, times(1)).publishToResponseTopic(any(), eq(msgEventTrackerTopic));
    }

    @Test
    void testParseXml_evtCdNotF95_char0to4() throws Exception {
        String xml = getXml("F96");
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("HDFC202501135000288850");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("MSGDEFIDR");
        doNothing().when(kafkaUtils).publishToResponseTopic(any(), any());

        admiXmlProcessor.parseXml(xml);
        verify(kafkaUtils, times(1)).publishToResponseTopic(any(), eq(msgEventTrackerTopic));
    }

    @Test
    void testParseXml_evtCdNotF95_char5to9() throws Exception {
        String xml = getXml("F96");
        when(utilityMethods.getBizMsgIdr(any())).thenReturn("HDFC202501135900288850");
        when(utilityMethods.getMsgDefIdr(any())).thenReturn("MSGDEFIDR");
        doNothing().when(kafkaUtils).publishToResponseTopic(any(), any());

        admiXmlProcessor.parseXml(xml);
        verify(kafkaUtils, times(1)).publishToResponseTopic(any(), eq(msgEventTrackerTopic));
    }

    @Test
    void testParseXml_invalidXml_throwsException() {
        String invalidXml = "<AppHdr><CreDt>2024-06-06</CreDt>"; // missing closing tags
        assertThrows(Exception.class, () -> admiXmlProcessor.parseXml(invalidXml));
    }
}

package com.hdfcbank.nilrouter.service.camt;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Body;
import com.hdfcbank.nilrouter.model.Header;
import com.hdfcbank.nilrouter.model.MessageEventTracker;
import com.hdfcbank.nilrouter.service.AuditService;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.StringReader;

@Slf4j
@Service
public class CamtXmlProcessor {

    @Value("${topic.fctopic}")
    private String fcTopic;

    @Value("${topic.ephtopic}")
    private String ephTopic;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;


    @Autowired
    AuditService auditService;

    @Autowired
    KafkaUtils kafkaUtils;

    @Autowired
    UtilityMethods utilityMethods;

    @ServiceActivator(inputChannel = "camt_52_54")
    public void parseXml(String xmlString) {

        String json = null;

        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document document = builder.parse(new InputSource(new StringReader(xmlString)));
            Header header = new Header();
            header.setMsgId(utilityMethods.getBizMsgIdr(document));
            header.setSource("NIL");
            header.setTargetFC(false);
            header.setTargetEPH(false);
            header.setTargetFCEPH(true);
            header.setFlowType("Inward");
            header.setMsgType(utilityMethods.getMsgDefIdr(document));

            Body body = new Body();
            body.setReqPayload(xmlString);
            body.setFcPayload(null);
            body.setEphPayload(null);

            MessageEventTracker wrapper = new MessageEventTracker();
            wrapper.setHeader(header);
            wrapper.setBody(body);

            ObjectMapper mapper = new ObjectMapper();

            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);


        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }

        log.info("CAMT52 | CAMT54 Json : {}", json);

        // Send to message-event-tracker-service topic
        kafkaUtils.publishToResponseTopic(json, msgEventTrackerTopic);

        // Send to FC and EPH topic
        kafkaUtils.publishToResponseTopic(xmlString, fcTopic);
        kafkaUtils.publishToResponseTopic(xmlString, ephTopic);
    }
}





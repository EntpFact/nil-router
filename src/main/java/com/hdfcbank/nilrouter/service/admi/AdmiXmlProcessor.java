package com.hdfcbank.nilrouter.service.admi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Body;
import com.hdfcbank.nilrouter.model.Header;
import com.hdfcbank.nilrouter.model.MessageEventTracker;
import com.hdfcbank.nilrouter.utils.Constants;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

@Service
public class AdmiXmlProcessor {

    @Autowired
    UtilityMethods utilityMethods;

    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private KafkaUtils kafkaUtils;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Autowired
    private ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "admi004")
    public void parseXml(String xmlString) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlString)));
        originalDoc.getDocumentElement().normalize();
        XPath xpath = XPathFactory.newInstance().newXPath();

        String msgId = utilityMethods.getBizMsgIdr(originalDoc);
        String CreDt = xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='CreDt']", originalDoc);
        String evtCd = xpath.evaluate("//*[local-name()='Document']//*[local-name()='EvtCd']", originalDoc);

        MessageEventTracker messageEventTracker = new MessageEventTracker();
        Header header = new Header();
        Body body = new Body();

        header.setMsgId(msgId);
        header.setMsgType(utilityMethods.getMsgDefIdr(originalDoc));
        header.setSource(Constants.NIL);
        header.setFlowType(Constants.INWARD);

        body.setReqPayload(xmlString);

        if (evtCd.equalsIgnoreCase("F95")) {

            header.setTargetFCEPH(true);
            header.setTargetFC(true);
            header.setTargetEPH(true);
            body.setFcPayload(xmlString);
            body.setEphPayload(xmlString);


        } else {

            char ch = msgId.charAt(13);
            if (ch >= '0' && ch <= '4') {
                header.setTargetFC(true);
                body.setFcPayload(xmlString);


            } else if (ch >= '5' && ch <= '9') {
                header.setTargetEPH(true);
                body.setEphPayload(xmlString);


            }
        }

        messageEventTracker.setHeader(header);
        messageEventTracker.setBody(body);

        String messageEventTrackerJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageEventTracker);

        kafkaUtils.publishToResponseTopic(messageEventTrackerJson, msgEventTrackerTopic);


    }
}

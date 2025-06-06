package com.hdfcbank.nilrouter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Body;
import com.hdfcbank.nilrouter.model.Header;
import com.hdfcbank.nilrouter.model.MessageEventTracker;
import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AuditService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private UtilityMethods utilityMethods;

    @Autowired
    private KafkaUtils kafkaUtils;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Value("${topic.sfmstopic}")
    private String sfmsTopic;


    public MsgEventTracker auditIncomingMessage(String xml) {
        MsgEventTracker msgEventTracker = new MsgEventTracker();
        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            msgEventTracker.setMsgId(utilityMethods.getBizMsgIdr(document));
            msgEventTracker.setOrgnlReq(xml);

            nilRepository.saveDataInMsgEventTracker(msgEventTracker);

        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            throw new RuntimeException(e);
        }
        return msgEventTracker;
    }


    public Map<String, String> constructOutwardJsonAndPublish(String xmlPayload) throws Exception {
        Map<String, String> map = new HashMap<>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        originalDoc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", originalDoc, XPathConstants.NODESET);


        MessageEventTracker messageEventTracker = new MessageEventTracker();
        Header header = new Header();
        Body body = new Body();
        header.setMsgId(utilityMethods.getBizMsgIdr(originalDoc));
        header.setSource("NIL");
        header.setMsgType(utilityMethods.getMsgDefIdr(originalDoc));
        header.setConsolidateAmt(utilityMethods.getTotalAmount(originalDoc));
        header.setFlowType("Outward");
        header.setTargetSFMS(true);
        header.setOrignlReqCount(txNodes.getLength());

        body.setReqPayload(xmlPayload);

        messageEventTracker.setBody(body);
        messageEventTracker.setHeader(header);

        String messageEventTrackerJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageEventTracker);

        //kafkaUtils.publishToResponseTopic(messageEventTrackerJson, msgEventTrackerTopic);
        map.put("MET",messageEventTrackerJson);

        //kafkaUtils.publishToResponseTopic(messageEventTracker.getBody().getReqPayload(), sfmsTopic);
        return map;

    }

}

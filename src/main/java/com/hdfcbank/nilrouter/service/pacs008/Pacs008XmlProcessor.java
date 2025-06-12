package com.hdfcbank.nilrouter.service.pacs008;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Body;
import com.hdfcbank.nilrouter.model.Header;
import com.hdfcbank.nilrouter.model.MessageEventTracker;
import com.hdfcbank.nilrouter.service.AuditService;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;


@Service
public class Pacs008XmlProcessor {

    @Autowired
    private InwardService inwardService;

    @Value("${topic.sfmstopic}")
    private String sfmsTopic;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Value("${cug_flag}")
    private String cugFlag;

    @Autowired
    private UtilityMethods utilityMethods;

    @Autowired
    private KafkaUtils kafkaUtils;

    @ServiceActivator(inputChannel = "pacs008")
    public void parseXml(String xmlString) throws Exception {

        if (utilityMethods.isOutward(xmlString)) {
            constructOutwardJsonAndPublish(xmlString);
        } else {
            if (cugFlag.equalsIgnoreCase("true")) {
                inwardService.processCugApproach(xmlString);
            } else {
                if (containsReturnTags(xmlString)) {
                    inwardService.processLateReturn(xmlString);
                } else {
                    inwardService.processFreshInward(xmlString);
                }
            }
        }
    }

    public void constructOutwardJsonAndPublish(String xmlPayload) throws Exception {
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

        kafkaUtils.publishToResponseTopic(messageEventTrackerJson, msgEventTrackerTopic);

        kafkaUtils.publishToResponseTopic(messageEventTracker.getBody().getReqPayload(), sfmsTopic);


    }

    public boolean containsReturnTags(String xmlPayload) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        doc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", doc, XPathConstants.NODESET);

        return IntStream.range(0, txNodes.getLength())
                .anyMatch(i -> {
                    Node tx = txNodes.item(i);

                    try {
                        // 1. Check InstrInf
                        Node instrInfNode = (Node) xpath.evaluate(
                                ".//*[local-name()='InstrForCdtrAgt']/*[local-name()='InstrInf']",
                                tx,
                                XPathConstants.NODE
                        );
                        if (instrInfNode != null && instrInfNode.getTextContent() != null) {
                            String text = instrInfNode.getTextContent().trim();
                            if (containsValidId(text)) {
                                return true;
                            }
                        }

                        // 2. Check all Ustrd
                        NodeList ustrdNodes = (NodeList) xpath.evaluate(
                                ".//*[local-name()='RmtInf']/*[local-name()='Ustrd']",
                                tx,
                                XPathConstants.NODESET
                        );

                        return IntStream.range(0, ustrdNodes.getLength())
                                .mapToObj(ustrdNodes::item)
                                .map(Node::getTextContent)
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .anyMatch(this::containsValidId);

                    } catch (XPathExpressionException e) {
                        throw new RuntimeException(e); // Or handle as needed
                    }
                });

//        return false; // No valid InstrInf or Ustrd found in any transaction
    }

    private boolean containsValidId(String text) {
        return Arrays.stream(text.split("[^A-Za-z0-9]"))
                .map(String::trim)
                .anyMatch(token ->
                        token.startsWith("HDFCN") &&
                                token.length() == 22 &&
                                Character.isDigit(token.charAt(14))
                );
    }



}





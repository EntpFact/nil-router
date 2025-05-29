package com.hdfcbank.nilrouter.service.pacs008;


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
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;


@Service
public class Pacs008XmlProcessor {

    @Autowired
    private InwardService inwardService;

    @Autowired
    AuditService auditService;


    @Autowired
    private CugApproach cugApproach;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Value("${cug_flag}")
    private String cugFlag;

    @Autowired
    private UtilityMethods utilityMethods;

    @ServiceActivator(inputChannel = "pacs008")
    public void parseXml(String xmlString) throws Exception {

        if (utilityMethods.isOutward(xmlString)) {
            auditService.constructOutwardJsonAndPublish(xmlString);
        } else {
            if (cugFlag.equalsIgnoreCase("true")) {
                cugApproach.processCugApproach(xmlString);
            } else {
                if (containsReturnTags(xmlString)) {
                    inwardService.processLateReturn(xmlString);
                } else {
                    inwardService.processFreshInward(xmlString);
                }
            }
        }
    }

    public boolean containsReturnTags(String xmlPayload) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        doc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", doc, XPathConstants.NODESET);

        for (int i = 0; i < txNodes.getLength(); i++) {
            Node tx = txNodes.item(i);

            // 1. Check InstrInf
            Node instrInfNode = (Node) xpath.evaluate(".//*[local-name()='InstrForCdtrAgt']/*[local-name()='InstrInf']", tx, XPathConstants.NODE);
            if (instrInfNode != null && instrInfNode.getTextContent() != null) {
                String text = instrInfNode.getTextContent().trim();
                if (containsValidId(text)) {
                    return true;
                }
            }

            // 2. Check all Ustrd
            NodeList ustrdNodes = (NodeList) xpath.evaluate(".//*[local-name()='RmtInf']/*[local-name()='Ustrd']", tx, XPathConstants.NODESET);
            for (int j = 0; j < ustrdNodes.getLength(); j++) {
                Node ustrdNode = ustrdNodes.item(j);
                if (ustrdNode != null && ustrdNode.getTextContent() != null) {
                    String text = ustrdNode.getTextContent().trim();
                    if (containsValidId(text)) {
                        return true;
                    }
                }
            }
        }

        return false; // No valid InstrInf or Ustrd found in any transaction
    }

    private boolean containsValidId(String text) {
        // Valid if it contains a substring that starts with HDFCN (Transaction ID)
        String[] tokens = text.split("[^A-Za-z0-9]");
        for (String token : tokens) {
            token = token.trim();
            if ((token.startsWith("HDFCN") && token.length() == 22 && Character.isDigit(token.charAt(14)))) {
                return true;
            }
        }
        return false;
    }


}





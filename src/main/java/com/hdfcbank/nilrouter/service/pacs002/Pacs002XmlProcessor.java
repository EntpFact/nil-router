package com.hdfcbank.nilrouter.service.pacs002;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Body;
import com.hdfcbank.nilrouter.model.Header;
import com.hdfcbank.nilrouter.model.MessageEventTracker;
import com.hdfcbank.nilrouter.model.Pacs002Fields;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hdfcbank.nilrouter.utils.Constants.INWARD;
import static com.hdfcbank.nilrouter.utils.Constants.NIL;


@Slf4j
@Service
public class Pacs002XmlProcessor {

    @Value("${topic.sfmstopic}")
    private String sfmsTopic;

    @Value("${topic.fctopic}")
    private String fcTopic;

    @Value("${topic.ephtopic}")
    private String ephTopic;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Autowired
    NilRepository dao;

    @Autowired
    UtilityMethods utilityMethods;

    @Autowired
    KafkaUtils kafkaUtils;

    @ServiceActivator(inputChannel = "pacs002")
    public void processXML(String xml) {

        List<Pacs002Fields> pacs002 = new ArrayList<>();

        Document fcOutputDoc;
        Document ephOutputDoc;
        String fcOutputDocString = null;
        String ephOutputDocString = null;

        String bizMsgIdr = null;
        String orgnlTxId = null;
        String orgnlEndToEndId = null;
        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document document = builder.parse(new InputSource(new StringReader(xml)));

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            bizMsgIdr = xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", document);

            boolean has0to4 = false, has5to9 = false, fcPresent = false, ephPresent = false, fcAndEphPresent = false;
            int ephCount = 0, fcCount = 0;

            NodeList txInfAndStsList = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']//*[local-name()='TxInfAndSts']", document, XPathConstants.NODESET);

            for (int i = 0; i < txInfAndStsList.getLength(); i++) {
                Element txInfAndSts = (Element) txInfAndStsList.item(i);

                orgnlTxId = xpath.evaluate("./*[local-name()='OrgnlTxId']", txInfAndSts);
                orgnlEndToEndId = xpath.evaluate("./*[local-name()='OrgnlEndToEndId']", txInfAndSts);

                int digit = extractOrgnlItmIdDigit(orgnlTxId);

                if (digit >= 0 && digit <= 4) {
                    has0to4 = true;
                    fcCount++;
                    pacs002.add(new Pacs002Fields(bizMsgIdr, orgnlEndToEndId, orgnlTxId, "FC"));
                } else if (digit >= 5 && digit <= 9) {
                    has5to9 = true;
                    ephCount++;
                    pacs002.add(new Pacs002Fields(bizMsgIdr, orgnlEndToEndId, orgnlTxId, "EPH"));
                } else if (digit == -1) {
                    // Check DB for the Pacs08 Inward Transaction by txn ID
                    String target = dao.findTargetByTxnId(orgnlTxId);
                    if ("FC".equalsIgnoreCase(target)) {
                        has0to4 = true;
                        fcCount++;
                        pacs002.add(new Pacs002Fields(bizMsgIdr, orgnlEndToEndId, orgnlTxId, "FC"));
                    } else if ("EPH".equalsIgnoreCase(target)) {
                        has5to9 = true;
                        ephCount++;
                        pacs002.add(new Pacs002Fields(bizMsgIdr, orgnlEndToEndId, orgnlTxId, "EPH"));
                    } else {
                        log.warn("Unknown target for txn ID: {}", orgnlTxId);
                    }
                }
            }


            if (has0to4 && !has5to9) {
                fcOutputDoc = filterTxInfAndSts(document, 0, 4);

                fcOutputDocString = documentToXml(fcOutputDoc);
                log.info("FC  : {}", fcOutputDocString);

                fcPresent = true;

                //Send to FC TOPIC
                kafkaUtils.publishToResponseTopic(xml, fcTopic);

            } else if (!has0to4 && has5to9) {
                ephOutputDoc = filterTxInfAndSts(document, 5, 9);
                ephOutputDocString = documentToXml(ephOutputDoc);
                log.info("EPH : {}", ephOutputDocString);

                ephPresent = true;


                //Send to EPH TOPIC
                kafkaUtils.publishToResponseTopic(xml, ephTopic);

            } else if (has0to4 && has5to9) {
                fcOutputDoc = filterTxInfAndSts(document, 0, 4);
                ephOutputDoc = filterTxInfAndSts(document, 5, 9);
                fcOutputDocString = documentToXml(fcOutputDoc);
                log.info("FC : {}", fcOutputDocString);
                ephOutputDocString = documentToXml(ephOutputDoc);
                log.info("EPH : {}", ephOutputDocString);

                fcAndEphPresent = true;

                //Send to FC & EPH TOPIC
                kafkaUtils.publishToResponseTopic(fcOutputDocString, fcTopic);
                kafkaUtils.publishToResponseTopic(ephOutputDocString, ephTopic);

            }


            Header header = new Header();
            header.setMsgId(utilityMethods.getBizMsgIdr(document));
            header.setSource(NIL);
            header.setTargetFC(fcPresent);
            header.setTargetEPH(ephPresent);
            header.setTargetFCEPH(fcAndEphPresent);
            header.setFlowType(INWARD);
            header.setMsgType(utilityMethods.getMsgDefIdr(document));
            header.setOrignlReqCount(pacs002.size());
            header.setIntermediateReqFCCount(fcCount);
            header.setIntermediateReqEPHCount(ephCount);

            Body body = new Body();
            body.setReqPayload(xml);
            body.setFcPayload(fcOutputDocString);
            body.setEphPayload(ephOutputDocString);

            MessageEventTracker wrapper = new MessageEventTracker();
            wrapper.setHeader(header);
            wrapper.setBody(body);

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
            log.info("Pacs002 Inward Json : {}", json);

            // Send to message-event-tracker-service topic
            kafkaUtils.publishToResponseTopic(json, msgEventTrackerTopic);


        } catch (Exception e) {
            log.error(e.toString());
        }
    }


    public Document filterTxInfAndSts(Document document, int minDigit, int maxDigit) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document newDoc = builder.newDocument();

        // Create <RequestPayload> root
        Element root = newDoc.createElement("RequestPayload");
        newDoc.appendChild(root);

        // Copy <AppHdr>
        Node appHdr = (Node) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='AppHdr']", document, XPathConstants.NODE);
        if (appHdr != null) {
            root.appendChild(newDoc.importNode(appHdr, true));
        }

        // Find <Document> and determine its namespace
        Node originalDocNode = (Node) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']", document, XPathConstants.NODE);
        if (originalDocNode == null) {
            throw new RuntimeException("Document element not found.");
        }

        String namespaceUri = originalDocNode.getNamespaceURI();
        Element newDocumentElem = newDoc.createElementNS(namespaceUri, "Document");
        root.appendChild(newDocumentElem);

        // Find <FIToFIPmtStsRpt>
        Node fiToFiRptNode = (Node) xpath.evaluate(".//*[local-name()='FIToFIPmtStsRpt']", originalDocNode, XPathConstants.NODE);
        if (fiToFiRptNode == null) {
            throw new RuntimeException("FIToFIPmtStsRpt element not found.");
        }

        Element newFIToFIPmtStsRpt = newDoc.createElementNS(namespaceUri, "FIToFIPmtStsRpt");
        newDocumentElem.appendChild(newFIToFIPmtStsRpt);

        // Copy <GrpHdr>
        Node grpHdr = (Node) xpath.evaluate("./*[local-name()='GrpHdr']", fiToFiRptNode, XPathConstants.NODE);
        if (grpHdr != null) {
            newFIToFIPmtStsRpt.appendChild(newDoc.importNode(grpHdr, true));
        }

        // Filter and append eligible <TxInfAndSts>
        NodeList txInfList = (NodeList) xpath.evaluate("./*[local-name()='TxInfAndSts']", fiToFiRptNode, XPathConstants.NODESET);
        for (int i = 0; i < txInfList.getLength(); i++) {
            Element txInf = (Element) txInfList.item(i);
            String orgnlTxId = xpath.evaluate("./*[local-name()='OrgnlTxId']", txInf);

            int digit = extractOrgnlItmIdDigit(orgnlTxId);
            if (digit == -1) {
                String target = dao.findTargetByTxnId(orgnlTxId);
                if ("FC".equalsIgnoreCase(target)) {
                    digit = 0; // Mark as acceptable for 0–4
                } else if ("EPH".equalsIgnoreCase(target)) {
                    digit = 9; // Mark as acceptable for 5–9
                }
            }

            // Apply digit filter
            if (digit >= minDigit && digit <= maxDigit) {
                Node imported = newDoc.importNode(txInf, true);
                newFIToFIPmtStsRpt.appendChild(imported);
            }
        }
        newDoc.setXmlStandalone(true); // Prevents automatic inclusion of standalone="no"
        return newDoc;
    }


    public String documentToXml(Document doc) throws TransformerException {
        StringWriter writer = new StringWriter();
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        // Perform the transformation
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }


    public static int extractOrgnlItmIdDigit(String orgnlItmId) {

        Pattern pattern = Pattern.compile("^HDFCN.{9}(.)");
        Matcher matcher = pattern.matcher(orgnlItmId);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

}
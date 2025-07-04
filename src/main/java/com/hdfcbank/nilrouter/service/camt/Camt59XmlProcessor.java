package com.hdfcbank.nilrouter.service.camt;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Body;
import com.hdfcbank.nilrouter.model.Camt59Fields;
import com.hdfcbank.nilrouter.model.Header;
import com.hdfcbank.nilrouter.model.MessageEventTracker;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.hdfcbank.nilrouter.utils.Constants.*;


@Slf4j
@Service
public class Camt59XmlProcessor {

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

    @ServiceActivator(inputChannel = "camt59")
    public void processXML(String xml) {

        if (utilityMethods.isOutward(xml)) {

            String json = null;

            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(xml)));

                XPath xpath = XPathFactory.newInstance().newXPath();
                NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='OrgnlNtfctnRef']", document, XPathConstants.NODESET);

                Header header = new Header();
                header.setMsgId(utilityMethods.getBizMsgIdr(document));
                header.setSource(NIL);
                header.setTargetFC(false);
                header.setTargetEPH(false);
                header.setTargetFCEPH(false);
                header.setTargetSFMS(true);
                header.setFlowType(OUTWARD);
                header.setMsgType(utilityMethods.getMsgDefIdr(document));
                header.setOrignlReqCount(txNodes.getLength());

                Body body = new Body();
                body.setReqPayload(xml);
                body.setFcPayload(null);
                body.setEphPayload(null);

                MessageEventTracker wrapper = new MessageEventTracker();
                wrapper.setHeader(header);
                wrapper.setBody(body);

                ObjectMapper mapper = new ObjectMapper();

                json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
                log.info("Camt59 Outward Json : {}", json);


            } catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
                throw new RuntimeException(e);
            }

            // Send to message-event-tracker-service topic
            kafkaUtils.publishToResponseTopic(json, msgEventTrackerTopic);
            //Send to SFMS
            kafkaUtils.publishToResponseTopic(xml, sfmsTopic);

        } else {
            processCamt59InwardMessage(xml);
        }
    }

    private void processCamt59InwardMessage(String xml) {
        List<Camt59Fields> camt59 = new ArrayList<>();

        Document fcOutputDoc, ephOutputDoc;
        String fcOutputDocString = null, ephOutputDocString = null;

        String bizMsgIdr = null, orgnlItmId = null, orgnlEndToEndId = null;
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

            NodeList orgnlItmAndStsList = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']//*[local-name()='OrgnlItmAndSts']", document, XPathConstants.NODESET);

            for (int i = 0; i < orgnlItmAndStsList.getLength(); i++) {
                Element orgnlItmAndSts = (Element) orgnlItmAndStsList.item(i);

                orgnlItmId = xpath.evaluate("./*[local-name()='OrgnlItmId']", orgnlItmAndSts);
                orgnlEndToEndId = xpath.evaluate("./*[local-name()='OrgnlEndToEndId']", orgnlItmAndSts);

                int digit = extractOrgnlItmIdDigit(orgnlItmId);


                if (digit >= 0 && digit <= 4) {
                    has0to4 = true;
                    fcCount++;
                    camt59.add(new Camt59Fields(bizMsgIdr, orgnlEndToEndId, orgnlItmId, FC));
                } else if (digit >= 5 && digit <= 9) {
                    has5to9 = true;
                    ephCount++;
                    camt59.add(new Camt59Fields(bizMsgIdr, orgnlEndToEndId, orgnlItmId, EPH));
                }
            }


            if (has0to4 && !has5to9) {
                fcOutputDoc = filterOrgnlItmAndSts(document, 0, 4);

                fcOutputDocString = documentToXml(fcOutputDoc);
                //log.info("FC  : {}", outputDocString);

                fcPresent = true;
                //Send to FC TOPIC
                kafkaUtils.publishToResponseTopic(fcOutputDocString, fcTopic);

            } else if (!has0to4 && has5to9) {
                ephOutputDoc = filterOrgnlItmAndSts(document, 5, 9);
                ephOutputDocString = documentToXml(ephOutputDoc);
                //log.info("EPH : {}", outputDocString);

                ephPresent = true;

                //Send to EPH TOPIC
                kafkaUtils.publishToResponseTopic(ephOutputDocString, ephTopic);


            } else if (has0to4 && has5to9) {
                fcOutputDoc = filterOrgnlItmAndSts(document, 0, 4);
                ephOutputDoc = filterOrgnlItmAndSts(document, 5, 9);
                fcOutputDocString = documentToXml(fcOutputDoc);
                //log.info("FC : {}", outputDocString);
                ephOutputDocString = documentToXml(ephOutputDoc);
                //log.info("EPH : {}", outputDocString1);

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
            header.setOrignlReqCount(camt59.size());
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
            log.info("Camt59 Inward Json : {}", json);

            // Send to message-event-tracker-service topic
            kafkaUtils.publishToResponseTopic(json, msgEventTrackerTopic);


        } catch (Exception e) {
            log.error(e.toString());
        }
    }


    private static Document filterOrgnlItmAndSts(Document document, int minDigit, int maxDigit) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document newDoc = builder.newDocument();

        // Copy root element <RequestPayload> without children
        Element root = (Element) newDoc.importNode(document.getDocumentElement(), false);
        newDoc.appendChild(root);

        //  Dynamically copy <AppHdr> preserving namespaces
        IntStream.range(0, document.getDocumentElement().getChildNodes().getLength())
                .mapToObj(i -> document.getDocumentElement().getChildNodes().item(i))
                .filter(node -> "AppHdr".equals(node.getLocalName()))
                .findFirst()
                .ifPresent(node -> root.appendChild(newDoc.importNode(node, true)));


        XPath xpath = XPathFactory.newInstance().newXPath();

        // Copy <Document> subtree if exists
        NodeList documentList = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']", document, XPathConstants.NODESET);
        if (documentList.getLength() > 0) {
            Element originalDocument = (Element) documentList.item(0);
            String namespaceUri = originalDocument.getNamespaceURI();
            Element newDocumentElem = newDoc.createElementNS(namespaceUri, "Document");
            root.appendChild(newDocumentElem);

            // Create and append <NtfctnToRcvStsRpt>
            Element ntfctnToRcvStsRpt = newDoc.createElementNS(namespaceUri, "NtfctnToRcvStsRpt");
            newDocumentElem.appendChild(ntfctnToRcvStsRpt);

            // Copy <GrpHdr>
            NodeList grpHdrList = originalDocument.getElementsByTagNameNS("*", "GrpHdr");
            if (grpHdrList.getLength() > 0) {
                ntfctnToRcvStsRpt.appendChild(newDoc.importNode(grpHdrList.item(0), true));
            }

            // Process <OrgnlNtfctnRef> and filter <OrgnlItmAndSts>
            NodeList orgnlNtfctnRefs = originalDocument.getElementsByTagNameNS("*", "OrgnlNtfctnRef");
            Element newOrgnlNtfctnAndSts = newDoc.createElementNS(namespaceUri, "OrgnlNtfctnAndSts");
            AtomicBoolean hasValidEntries = new AtomicBoolean(false);

            IntStream.range(0, orgnlNtfctnRefs.getLength())
                    .mapToObj(i -> (Element) orgnlNtfctnRefs.item(i))
                    .forEach(orgnlNtfctnRef -> {
                        NodeList itmAndStsList = orgnlNtfctnRef.getElementsByTagNameNS("*", "OrgnlItmAndSts");

                        IntStream.range(0, itmAndStsList.getLength())
                                .mapToObj(j -> (Element) itmAndStsList.item(j))
                                .filter(orgnlItmAndSts -> {
                                    try {
                                        String orgnlItmId = xpath.evaluate("./*[local-name()='OrgnlItmId']", orgnlItmAndSts);
                                        int digit = extractOrgnlItmIdDigit(orgnlItmId);
                                        return digit >= minDigit && digit <= maxDigit;
                                    } catch (XPathExpressionException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                                .forEach(orgnlItmAndSts -> {
                                    Element newOrgnlNtfctnRef = newDoc.createElementNS(namespaceUri, "OrgnlNtfctnRef");

                                    NodeList dbtrAgtList = orgnlNtfctnRef.getElementsByTagNameNS("*", "DbtrAgt");
                                    if (dbtrAgtList.getLength() > 0) {
                                        newOrgnlNtfctnRef.appendChild(newDoc.importNode(dbtrAgtList.item(0), true));
                                    }

                                    newOrgnlNtfctnRef.appendChild(newDoc.importNode(orgnlItmAndSts, true));
                                    newOrgnlNtfctnAndSts.appendChild(newOrgnlNtfctnRef);
                                    hasValidEntries.set(true);
                                });
                    });


            if (hasValidEntries.get()) {
                ntfctnToRcvStsRpt.appendChild(newOrgnlNtfctnAndSts);
            }
        }

        newDoc.setXmlStandalone(true);
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


    private static int extractOrgnlItmIdDigit(String orgnlItmId) {

        Pattern pattern = Pattern.compile("^.{14}(.)"); // 14 characters, then capture the 15th
        Matcher matcher = pattern.matcher(orgnlItmId);

        if (matcher.find()) {
            return Character.getNumericValue(matcher.group(1).charAt(0));
        }
        return -1;
    }

}
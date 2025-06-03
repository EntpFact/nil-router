package com.hdfcbank.nilrouter.service.pacs004;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.*;
import com.hdfcbank.nilrouter.service.AuditService;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hdfcbank.nilrouter.utils.Constants.*;


@Slf4j
@Service

public class Pacs004XmlProcessor {


    @Autowired
    NilRepository dao;

    @Autowired
    UtilityMethods utilityMethods;

    @Autowired
    KafkaUtils kafkautils;

    @Value("${topic.fctopic}")
    String fcTopic;


    @Value("${topic.ephtopic}")
    String ephTopic;

    @Value("${topic.sfmstopic}")
    private String sfmstopic;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Autowired
    AuditService outwardService;

    @ServiceActivator(inputChannel = "pacs004")
    public void parseXml(String xmlString) throws Exception {

       if (utilityMethods.isOutward(xmlString)) {
            String json = null;

            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();

                Document document = builder.parse(new InputSource(new StringReader(xmlString)));
                Header header = new Header();
                header.setMsgId(utilityMethods.getBizMsgIdr(document));
                header.setSource(NIL);
                header.setTargetFC(false);
                header.setTargetEPH(false);
                header.setTargetFCEPH(false);
                header.setTargetSFMS(true);
                header.setFlowType(OUTWARD);
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

                // Send json to Message Tracker service
                kafkautils.publishToResponseTopic(json, msgEventTrackerTopic);


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
           log.info("Pacs04 Outward : {}", json);


            //Send to SFMS
            kafkautils.publishToResponseTopic(xmlString, sfmstopic);

        } else {
            processXML(xmlString);
        }

    }

    public void processXML(String xml) {
        List<Pacs004Fields> pacs004 = new ArrayList<>();
        String bizMsgIdr = null, orgnlItmId = null, orgnlEndToEndId = null;
        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document document = builder.parse(new InputSource(new StringReader(xml)));

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            bizMsgIdr = xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", document);
            boolean fcPresent = false, ephPresent = false, fcAndEphPresent = false;
            boolean has0to4 = false, has5to9 = false;
            Integer ephCount = 0;
            Integer fcCount = 0;


            NodeList orgnlItmAndStsList = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']//*[local-name()='TxInf']", document, XPathConstants.NODESET);
            double consolidateAmountFC = 0;
            double consolidateAmountEPH = 0;
            for (int i = 0; i < orgnlItmAndStsList.getLength(); i++) {
                Element orgnlItmAndSts = (Element) orgnlItmAndStsList.item(i);
                String batchIdValue = "";
                orgnlItmId = xpath.evaluate("./*[local-name()='OrgnlTxId']", orgnlItmAndSts);
                orgnlEndToEndId = xpath.evaluate("./*[local-name()='OrgnlEndToEndId']", orgnlItmAndSts);
                String amount = xpath.evaluate("./*[local-name()='RtrdIntrBkSttlmAmt']", orgnlItmAndSts);

                NodeList batchIdList = (NodeList) xpath.evaluate(".//*[local-name()='OrgnlTxRef']/*[local-name()='UndrlygCstmrCdtTrf']//*[local-name()='RmtInf']", orgnlItmAndSts, XPathConstants.NODE);
                if (batchIdList != null) {
                    for (int j = 0; j < batchIdList.getLength(); j++) {
                        String batchId = (batchIdList.item(j)).getTextContent();
                        if (batchId.contains("BatchId")) {
                            batchIdValue = batchId;
                        }

                    }
                }
                int digit = extractEndToEndIdDigit(orgnlItmId);


                if (digit >= 0 && digit <= 4) {
                    has0to4 = true;
                    consolidateAmountFC = consolidateAmountFC + Double.parseDouble(amount);
                    fcCount++;
                    pacs004.add(new Pacs004Fields(bizMsgIdr, orgnlEndToEndId, orgnlItmId, amount, FC, batchIdValue));
                } else if (digit >= 5 && digit <= 9) {
                    has5to9 = true;
                    ephCount++;
                    consolidateAmountEPH += Double.parseDouble(amount);
                    pacs004.add(new Pacs004Fields(bizMsgIdr, orgnlEndToEndId, orgnlItmId, amount, EPH, batchIdValue));
                }
            }

            String outputDocString = null;
            String outputDocString1 = null;
            if (has0to4 && !has5to9) {
                Document outputDoc = filterOrgnlItmAndSts(document, 0, 4, fcCount, consolidateAmountFC);

                outputDocString = documentToXml(document);
                log.info("FC  : {}", outputDocString);

                fcPresent = true;
                kafkautils.publishToResponseTopic(outputDocString, fcTopic);

            } else if (!has0to4 && has5to9) {
                Document outputDoc = filterOrgnlItmAndSts(document, 5, 9, ephCount, consolidateAmountEPH);
                outputDocString = documentToXml(outputDoc);
                log.info("EPH : {}", outputDocString);

                ephPresent = true;
                kafkautils.publishToResponseTopic(outputDocString, ephTopic);
            } else if (has0to4 && has5to9) {
                Document outputDoc1 = filterOrgnlItmAndSts(document, 0, 4, fcCount, consolidateAmountFC);
                Document outputDoc2 = filterOrgnlItmAndSts(document, 5, 9, ephCount, consolidateAmountEPH);
                outputDocString = documentToXml(outputDoc1);
                log.info("FC : {}", outputDocString);
                outputDocString1 = documentToXml(outputDoc2);
                log.info("EPH : {}", outputDocString1);

                kafkautils.publishToResponseTopic(outputDocString, fcTopic);

                fcAndEphPresent = true;
                kafkautils.publishToResponseTopic(outputDocString1, ephTopic);
            }
            Header header = new Header();
            header.setMsgId(utilityMethods.getBizMsgIdr(document));
            header.setSource(NIL);
            header.setTargetFC(fcPresent);
            header.setTargetEPH(ephPresent);
            header.setTargetFCEPH(fcAndEphPresent);
            header.setFlowType(INWARD);
            header.setMsgType(utilityMethods.getMsgDefIdr(document));
            header.setOrignlReqCount(pacs004.size());
            header.setConsolidateAmt(BigDecimal.valueOf(consolidateAmountEPH + consolidateAmountFC));
            header.setConsolidateAmtEPH(BigDecimal.valueOf(consolidateAmountEPH));
            header.setConsolidateAmtFC(BigDecimal.valueOf(consolidateAmountFC));
            header.setIntermediateReqFCCount(fcCount);
            header.setIntermediateReqEPHCount(ephCount);

            Body body = new Body();
            body.setReqPayload(xml);
            body.setFcPayload(outputDocString);
            body.setEphPayload(outputDocString1);

            MessageEventTracker wrapper = new MessageEventTracker();
            wrapper.setHeader(header);
            wrapper.setBody(body);

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
            log.info("Pacs04 Inward : {}", json);

            // Send json to Message Tracker service
            kafkautils.publishToResponseTopic(json, msgEventTrackerTopic);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private static Document filterOrgnlItmAndSts(Document document, int minDigit, int maxDigit, int count, double total) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();


        Document newDoc = builder.newDocument();

        Element root = (Element) newDoc.importNode(document.getDocumentElement(), false);
        newDoc.appendChild(root);

        XPath xpath = XPathFactory.newInstance().newXPath();

        NodeList appHdrList = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='AppHdr']", document, XPathConstants.NODESET);
        if (appHdrList.getLength() > 0) {

            root.appendChild(newDoc.importNode(appHdrList.item(0), true));
        }

        NodeList documentList = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']", document, XPathConstants.NODESET);
        if (documentList.getLength() > 0) {
            Element originalDocument = (Element) documentList.item(0);
            Element newDocument = (Element) newDoc.importNode(originalDocument, false);
            root.appendChild(newDocument);

            Element PmtRtr = createElementNS(newDoc, "PmtRtr");
            newDocument.appendChild(PmtRtr);

            NodeList grpHdrList = (NodeList) xpath.evaluate(".//*[local-name()='GrpHdr']", originalDocument, XPathConstants.NODESET);
            if (grpHdrList.getLength() > 0) {
                NodeList nbOfTxsList = ((Element) grpHdrList.item(0)).getElementsByTagNameNS("*", "NbOfTxs");
                NodeList tlRtrdIntrBkSttlmAmtList = ((Element) grpHdrList.item(0)).getElementsByTagNameNS("*", "TtlRtrdIntrBkSttlmAmt");

                if (nbOfTxsList.getLength() > 0) {
                    Element nbOfTxs = (Element) nbOfTxsList.item(0);
                    nbOfTxs.setTextContent(String.valueOf(count));

                }
                if (tlRtrdIntrBkSttlmAmtList.getLength() > 0) {
                    Element tlRtrdIntrBkSttlmAmt = (Element) tlRtrdIntrBkSttlmAmtList.item(0);
                    tlRtrdIntrBkSttlmAmt.setTextContent(String.valueOf(total));
                }

                PmtRtr.appendChild(newDoc.importNode(grpHdrList.item(0), true));

            }

            NodeList txInf = (NodeList) xpath.evaluate(".//*[local-name()='TxInf']", originalDocument, XPathConstants.NODESET);
            Element newTxInf = createElementNS(newDoc, "Info");

            boolean hasValidEntries = false;

            for (int i = 0; i < txInf.getLength(); i++) {
                Element orgnlNtfctnRef = (Element) txInf.item(i);
                String orgnlTxId = xpath.evaluate("./*[local-name()='OrgnlTxId']", orgnlNtfctnRef);

                int digit = extractEndToEndIdDigit(orgnlTxId);
                if (digit >= minDigit && digit <= maxDigit) {
                    newTxInf.appendChild(newDoc.importNode(orgnlNtfctnRef, true));
                    hasValidEntries = true;
                }
            }

            if (hasValidEntries) {
                PmtRtr.appendChild(newTxInf);
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


    private static Element createElementNS(Document doc, String localName) {
        final String PACS_NS = "urn:iso:std:iso:20022:tech:xsd:pacs.004.001.10";
        return doc.createElementNS(PACS_NS, localName);
    }

    private static int extractEndToEndIdDigit(String endToEndId) {
      /*  Pattern pattern = Pattern.compile("/XUTR/HDFCH(\\d{9})");
        Matcher matcher = pattern.matcher(endToEndId);

        if (matcher.find()) {
            return Character.getNumericValue(matcher.group(1).charAt(0));
        }*/

        Pattern pattern = Pattern.compile("^.{14}(.)"); // 14 characters, then capture the 15th
        Matcher matcher = pattern.matcher(endToEndId);

        if (matcher.find()) {
            return Character.getNumericValue(matcher.group(1).charAt(0));
        }
        return -1;
    }

}
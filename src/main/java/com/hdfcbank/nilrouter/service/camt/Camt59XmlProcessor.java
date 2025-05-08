package com.hdfcbank.nilrouter.service.camt;


import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Camt59Fields;
import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.model.TransactionAudit;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Service
public class Camt59XmlProcessor {

    @Value("${topic.sfmstopic}")
    private String sfmstopic;

    @Value("${topic.fctopic}")
    private String fctopic;

    @Value("${topic.ephtopic}")
    private String ephtopic;

    @Autowired
    NilRepository dao;

    @Autowired
    UtilityMethods utilityMethods;

    @Autowired
    AuditService auditService;

    @Autowired
    KafkaUtils kafkaUtils;

    @ServiceActivator(inputChannel = "camt59")
    public void processXML(String xml) {

        if (utilityMethods.isOutward(xml)) {
            auditService.auditData(xml);
            //Send to SFMS
            kafkaUtils.publishToResponseTopic(xml, sfmstopic);

        } else {
            processCamt59InwardMessage(xml);
        }
    }

    private void processCamt59InwardMessage(String xml) {
        List<Camt59Fields> camt59 = new ArrayList<>();
        String bizMsgIdr = null, orgnlItmId = null, orgnlEndToEndId = null;
        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document document = builder.parse(new InputSource(new StringReader(xml)));

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            bizMsgIdr = xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", document);

            boolean has0to4 = false, has5to9 = false;
            int ephCount = 0, fcCount = 0;

            NodeList orgnlItmAndStsList = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']//*[local-name()='OrgnlItmAndSts']", document, XPathConstants.NODESET);

            for (int i = 0; i < orgnlItmAndStsList.getLength(); i++) {
                Element orgnlItmAndSts = (Element) orgnlItmAndStsList.item(i);

                orgnlItmId = xpath.evaluate("./*[local-name()='OrgnlItmId']", orgnlItmAndSts);
                orgnlEndToEndId = xpath.evaluate("./*[local-name()='OrgnlEndToEndId']", orgnlItmAndSts);

                int digit = extractEndToEndIdDigit(orgnlEndToEndId);


                if (digit >= 0 && digit <= 4) {
                    has0to4 = true;
                    fcCount++;
                    camt59.add(new Camt59Fields(bizMsgIdr, orgnlItmId, orgnlEndToEndId, "FC"));
                } else if (digit >= 5 && digit <= 9) {
                    has5to9 = true;
                    ephCount++;
                    camt59.add(new Camt59Fields(bizMsgIdr, orgnlItmId, orgnlEndToEndId, "EPH"));
                }
            }


            if (has0to4 && !has5to9) {
                Document outputDoc = filterOrgnlItmAndSts(document, 0, 4);

                String outputDocString = documentToXml(document);
                //log.info("FC  : {}", outputDocString);

                MsgEventTracker tracker = new MsgEventTracker();
                tracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                tracker.setSource("NIL");
                tracker.setTarget("FC");
                tracker.setFlowType("Inward");
                tracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                tracker.setOrgnlReq(xml);

                //Send to FC TOPIC
                kafkaUtils.publishToResponseTopic(xml,fctopic);
                // Save to DB
                dao.saveDataInMsgEventTracker(tracker);


            } else if (!has0to4 && has5to9) {
                Document outputDoc = filterOrgnlItmAndSts(document, 5, 9);
                String outputDocString = documentToXml(outputDoc);
                //log.info("EPH : {}", outputDocString);

                MsgEventTracker tracker = new MsgEventTracker();
                tracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                tracker.setSource("NIL");
                tracker.setTarget("EPH");
                tracker.setFlowType("Inward");
                tracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                tracker.setOrgnlReq(xml);

                //Send to EPH TOPIC
                kafkaUtils.publishToResponseTopic(xml,ephtopic);
                // Save to DB
                dao.saveDataInMsgEventTracker(tracker);

            } else if (has0to4 && has5to9) {
                Document outputDoc1 = filterOrgnlItmAndSts(document, 0, 4);
                Document outputDoc2 = filterOrgnlItmAndSts(document, 5, 9);
                String outputDocString = documentToXml(outputDoc1);
                //log.info("FC : {}", outputDocString);
                String outputDocString1 = documentToXml(outputDoc2);
                //log.info("EPH : {}", outputDocString1);

                MsgEventTracker fcTracker = new MsgEventTracker();
                fcTracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                fcTracker.setSource("NIL");
                fcTracker.setTarget("FC");
                fcTracker.setFlowType("Inward");
                fcTracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                fcTracker.setOrgnlReq(xml);
                fcTracker.setIntermediateReq(outputDocString);
                fcTracker.setOrgnlReqCount(camt59.size());
                fcTracker.setIntermediateCount(fcCount);

                MsgEventTracker ephTracker = new MsgEventTracker();
                ephTracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                ephTracker.setSource("NIL");
                ephTracker.setTarget("EPH");
                ephTracker.setFlowType("Inward");
                ephTracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                ephTracker.setOrgnlReq(xml);
                ephTracker.setIntermediateReq(outputDocString1);
                ephTracker.setOrgnlReqCount(camt59.size());
                ephTracker.setIntermediateCount(ephCount);


                //Send to FC & EPH TOPIC
                kafkaUtils.publishToResponseTopic(outputDocString,fctopic);
                kafkaUtils.publishToResponseTopic(outputDocString1,ephtopic);
                // Save to DB
                dao.saveDataInMsgEventTracker(fcTracker);
                dao.saveDataInMsgEventTracker(ephTracker);
            }

            List<TransactionAudit> transactionAudits = extractCamt59Transactions(document, xml, camt59);

            dao.saveAllTransactionAudits(transactionAudits);

        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    public List<TransactionAudit> extractCamt59Transactions(Document originalDoc, String xml, List<Camt59Fields> camt59Fields) throws XPathExpressionException {
        List<TransactionAudit> listOfTransactions = new ArrayList<>();

        String msgId = utilityMethods.getMsgDefIdr(originalDoc);

        for (Camt59Fields camt59 : camt59Fields) {

            TransactionAudit transaction = new TransactionAudit();
            transaction.setMsgId(msgId);
            transaction.setEndToEndId(camt59.getEndToEndId());
            transaction.setTxnId(camt59.getTxId());
            //transaction.setAmount(new BigDecimal(evaluateText(xpath, txNode, ".//*[local-name()='Amt']")));
            transaction.setMsgType("camt.059.001.06");
            transaction.setSource("NIL");
            transaction.setTarget(camt59.getSwtch());
            transaction.setFlowType("Inward");
            transaction.setReqPayload(xml);

            listOfTransactions.add(transaction);
        }
        return listOfTransactions;
    }


    private static Document filterOrgnlItmAndSts(Document document, int minDigit, int maxDigit) throws Exception {
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

            Element ntfctnToRcvStsRpt = createElementNS(newDoc, "NtfctnToRcvStsRpt");
            newDocument.appendChild(ntfctnToRcvStsRpt);

            NodeList grpHdrList = (NodeList) xpath.evaluate(".//*[local-name()='GrpHdr']", originalDocument, XPathConstants.NODESET);
            if (grpHdrList.getLength() > 0) {
                ntfctnToRcvStsRpt.appendChild(newDoc.importNode(grpHdrList.item(0), true));
            }

            NodeList orgnlNtfctnRefs = (NodeList) xpath.evaluate(".//*[local-name()='OrgnlNtfctnRef']", originalDocument, XPathConstants.NODESET);
            Element newOrgnlNtfctnAndSts = createElementNS(newDoc, "OrgnlNtfctnAndSts");
            boolean hasValidEntries = false;

            for (int i = 0; i < orgnlNtfctnRefs.getLength(); i++) {
                Element orgnlNtfctnRef = (Element) orgnlNtfctnRefs.item(i);
                NodeList itmAndStsList = (NodeList) xpath.evaluate(".//*[local-name()='OrgnlItmAndSts']", orgnlNtfctnRef, XPathConstants.NODESET);

                for (int j = 0; j < itmAndStsList.getLength(); j++) {
                    Element orgnlItmAndSts = (Element) itmAndStsList.item(j);
                    String endToEndId = xpath.evaluate("./*[local-name()='OrgnlEndToEndId']", orgnlItmAndSts);

                    int digit = extractEndToEndIdDigit(endToEndId);
                    if (digit >= minDigit && digit <= maxDigit) {
                        Element newOrgnlNtfctnRef = createElementNS(newDoc, "OrgnlNtfctnRef");

                        NodeList dbtrAgtList = (NodeList) xpath.evaluate(".//*[local-name()='DbtrAgt']", orgnlNtfctnRef, XPathConstants.NODESET);
                        if (dbtrAgtList.getLength() > 0) {
                            newOrgnlNtfctnRef.appendChild(newDoc.importNode(dbtrAgtList.item(0), true));
                        }

                        newOrgnlNtfctnRef.appendChild(newDoc.importNode(orgnlItmAndSts, true));
                        newOrgnlNtfctnAndSts.appendChild(newOrgnlNtfctnRef);
                        hasValidEntries = true;
                    }
                }
            }

            if (hasValidEntries) {
                ntfctnToRcvStsRpt.appendChild(newOrgnlNtfctnAndSts);
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
        final String CAMT_NS = "urn:iso:std:iso:20022:tech:xsd:camt.059.001.06";
        return doc.createElementNS(CAMT_NS, localName);
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
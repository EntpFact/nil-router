package com.hdfcbank.nilrouter.service.pacs002;


import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.model.Pacs002Fields;
import com.hdfcbank.nilrouter.model.TransactionAudit;
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
public class Pacs002XmlProcessor {

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
    KafkaUtils kafkaUtils;

    @ServiceActivator(inputChannel = "pacs002")
    public void processXML(String xml) {


        processPacs002InwardMessage(xml);

    }

    private void processPacs002InwardMessage(String xml) {
        List<Pacs002Fields> pacs002 = new ArrayList<>();
        String bizMsgIdr = null, orgnlTxId = null, orgnlEndToEndId = null;
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
                Document outputDoc = filterTxInfAndSts(document, 0, 4);

                String outputDocString = documentToXml(document);
                log.info("FC  : {}", outputDocString);

                MsgEventTracker tracker = new MsgEventTracker();
                tracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                tracker.setSource("NIL");
                tracker.setTarget("FC");
                tracker.setFlowType("Inward");
                tracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                tracker.setOrgnlReq(xml);

                //Send to FC TOPIC
                kafkaUtils.publishToResponseTopic(xml, fctopic);
                // Save to DB
                dao.saveDataInMsgEventTracker(tracker);


            } else if (!has0to4 && has5to9) {
                Document outputDoc = filterTxInfAndSts(document, 5, 9);
                String outputDocString = documentToXml(outputDoc);
                log.info("EPH : {}", outputDocString);

                MsgEventTracker tracker = new MsgEventTracker();
                tracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                tracker.setSource("NIL");
                tracker.setTarget("EPH");
                tracker.setFlowType("Inward");
                tracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                tracker.setOrgnlReq(xml);

                //Send to EPH TOPIC
                kafkaUtils.publishToResponseTopic(xml, ephtopic);
                // Save to DB
                dao.saveDataInMsgEventTracker(tracker);

            } else if (has0to4 && has5to9) {
                Document outputDoc1 = filterTxInfAndSts(document, 0, 4);
                Document outputDoc2 = filterTxInfAndSts(document, 5, 9);
                String outputDocString = documentToXml(outputDoc1);
                log.info("FC : {}", outputDocString);
                String outputDocString1 = documentToXml(outputDoc2);
                log.info("EPH : {}", outputDocString1);

                MsgEventTracker fcTracker = new MsgEventTracker();
                fcTracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                fcTracker.setSource("NIL");
                fcTracker.setTarget("FC");
                fcTracker.setFlowType("Inward");
                fcTracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                fcTracker.setOrgnlReq(xml);
                fcTracker.setIntermediateReq(outputDocString);
                fcTracker.setOrgnlReqCount(pacs002.size());
                fcTracker.setIntermediateCount(fcCount);

                MsgEventTracker ephTracker = new MsgEventTracker();
                ephTracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                ephTracker.setSource("NIL");
                ephTracker.setTarget("EPH");
                ephTracker.setFlowType("Inward");
                ephTracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                ephTracker.setOrgnlReq(xml);
                ephTracker.setIntermediateReq(outputDocString1);
                ephTracker.setOrgnlReqCount(pacs002.size());
                ephTracker.setIntermediateCount(ephCount);


                //Send to FC & EPH TOPIC
                kafkaUtils.publishToResponseTopic(outputDocString, fctopic);
                kafkaUtils.publishToResponseTopic(outputDocString1, ephtopic);
                // Save to DB
                dao.saveDataInMsgEventTracker(fcTracker);
                dao.saveDataInMsgEventTracker(ephTracker);
            }

            List<TransactionAudit> transactionAudits = extractPacs002Transactions(document, xml, pacs002);

            dao.saveAllTransactionAudits(transactionAudits);

        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    public List<TransactionAudit> extractPacs002Transactions(Document originalDoc, String xml, List<Pacs002Fields> pacs002Fields) throws XPathExpressionException {
        List<TransactionAudit> listOfTransactions = new ArrayList<>();

        String msgId = utilityMethods.getBizMsgIdr(originalDoc);
        String msgType = utilityMethods.getMsgDefIdr(originalDoc);

        for (Pacs002Fields pacs002 : pacs002Fields) {

            TransactionAudit transaction = new TransactionAudit();
            transaction.setMsgId(msgId);
            transaction.setEndToEndId(pacs002.getEndToEndId());
            transaction.setTxnId(pacs002.getTxId());
            //transaction.setAmount(new BigDecimal(evaluateText(xpath, txNode, ".//*[local-name()='Amt']")));
            transaction.setMsgType(msgType);
            transaction.setSource("NIL");
            transaction.setTarget(pacs002.getSwtch());
            transaction.setFlowType("Inward");
            transaction.setReqPayload(xml);

            listOfTransactions.add(transaction);
        }
        return listOfTransactions;
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


    private static int extractOrgnlItmIdDigit(String orgnlItmId) {

        Pattern pattern = Pattern.compile("^HDFCN.{9}(.)");
        Matcher matcher = pattern.matcher(orgnlItmId);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

}
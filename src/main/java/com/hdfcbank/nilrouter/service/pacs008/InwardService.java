package com.hdfcbank.nilrouter.service.pacs008;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.*;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class InwardService {

    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private KafkaUtils kafkaUtils;

    @Value("${topic.fctopic}")
    private String fctopic;

    @Value("${topic.ephtopic}")
    private String ephtopic;

    @Autowired
    private UtilityMethods utilityMethods;

    @Autowired
    private ObjectMapper objectMapper;

    public void processFreshInward(String xmlPayload) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        originalDoc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();
        String msgId = utilityMethods.getBizMsgIdr(originalDoc);
        String msgType = utilityMethods.getMsgDefIdr(originalDoc);
        BigDecimal totalAmount = utilityMethods.getTotalAmount(originalDoc);
        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", originalDoc, XPathConstants.NODESET);
        List<TransactionAudit> listOfTransactions = new ArrayList<>();

        String substring = msgId.substring(4);
        BigInteger numericMsgId = new BigInteger(substring);
        int mod = numericMsgId.mod(BigInteger.valueOf(10)).intValue();
        boolean fcPresent = false;
        boolean ephPresent = false;
        if (mod >= 0 && mod <= 4) {
            fcPresent = true;
        } else if (mod >= 5 && mod <= 9) {
            ephPresent = true;
        }

//        MsgEventTracker tracker = new MsgEventTracker();
//        tracker.setMsgId(msgId);
//        tracker.setSource("NIL");
//        tracker.setTarget(target);
//        tracker.setFlowType("inward");
//        tracker.setMsgType(msgType);
//        tracker.setOrgnlReq(xmlPayload);
//        tracker.setConsolidateAmt(totalAmount);
//        tracker.setOrgnlReqCount(txNodes.getLength());


//
//        for (int i = 0; i < txNodes.getLength(); i++) {
//            Node txNode = txNodes.item(i);
//
//            TransactionAudit transaction = new TransactionAudit();
//            transaction.setMsgId(msgId);
//            transaction.setEndToEndId(utilityMethods.evaluateText(xpath, txNode, ".//*[local-name()='EndToEndId']"));
//            transaction.setTxnId(utilityMethods.evaluateText(xpath, txNode, ".//*[local-name()='TxId']"));
//            transaction.setAmount(new BigDecimal(utilityMethods.evaluateText(xpath, txNode, ".//*[local-name()='IntrBkSttlmAmt']")));
//            transaction.setBatchId(utilityMethods.evaluateText(xpath, txNode, ".//*[local-name()='RmtInf']//*[local-name()='Ustrd']"));
//            transaction.setSource("NIL");
//            transaction.setTarget(target);
//            transaction.setFlowType("inward");
//            transaction.setMsgType(msgType);
//            transaction.setReqPayload(xmlPayload);
//
//            listOfTransactions.add(transaction);
//
//        }

//        nilRepository.saveDataInMsgEventTracker(tracker);
//
//        nilRepository.saveAllTransactionAudits(listOfTransactions);

        MessageEventTracker messageEventTracker = new MessageEventTracker();

        Body body = new Body();
        body.setReqPayload(xmlPayload);

        Header header = new Header();

        header.setMsgId(msgId);
        header.setMsgType(msgType);
        header.setSource("NIL");
        header.setTargetEPH(ephPresent);
        header.setTargetFC(fcPresent);
        header.setFlowType("Inward");
        header.setConsolidateAmt(totalAmount);
        header.setOrignlReqCount(txNodes.getLength());
        if (fcPresent) {
            header.setConsolidateAmtFC(totalAmount);
            header.setIntermediateReqFCCount(txNodes.getLength());
            body.setFcPayload(xmlPayload);
        } else {
            header.setConsolidateAmtEPH(totalAmount);
            header.setIntermediateReqEPHCount(txNodes.getLength());
            body.setEphPayload(xmlPayload);
        }
        messageEventTracker.setHeader(header);
        messageEventTracker.setBody(body);

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageEventTracker);


//        if (target.equalsIgnoreCase("FC")) {
//            kafkaUtils.publishToResponseTopic(xmlPayload, fctopic);
//        } else {
//            kafkaUtils.publishToResponseTopic(xmlPayload, ephtopic);
//
//        }


    }

    public void processLateReturn(String xmlPayload) throws Exception {

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        originalDoc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();

        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", originalDoc, XPathConstants.NODESET);

        List<Node> Fc = new ArrayList<>();
        List<Node> EPH = new ArrayList<>();
        List<Node> freshTxns = new ArrayList<>();
//        List<TransactionAudit> listOfTransactions = new ArrayList<>();
        BigDecimal fcTotal = BigDecimal.valueOf(0);
        BigDecimal ephTotal = BigDecimal.valueOf(0);
        BigDecimal freshTotal = BigDecimal.valueOf(0);


        MessageEventTracker messageEventTracker = new MessageEventTracker();

        Body body = new Body();
        body.setReqPayload(xmlPayload);

        Header header = new Header();
        header.setMsgType(utilityMethods.getMsgDefIdr(originalDoc));
        header.setMsgId(utilityMethods.getBizMsgIdr(originalDoc));
        header.setSource("NIL");
        header.setFlowType("Inward");
        header.setOrignlReqCount(txNodes.getLength());
        header.setConsolidateAmt(utilityMethods.getTotalAmount(originalDoc));


        for (int i = 0; i < txNodes.getLength(); i++) {
            Node tx = txNodes.item(i);
            String id = extractTransactionIdentifier(tx, xpath);

//            TransactionAudit transaction = new TransactionAudit();
//            transaction.setMsgId(msgId);
//            transaction.setEndToEndId(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='EndToEndId']"));
//            transaction.setTxnId(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='TxId']"));
//            transaction.setAmount(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));
//            transaction.setBatchId(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='RmtInf']//*[local-name()='Ustrd']"));
//            transaction.setSource("NIL");
//            transaction.setFlowType("inward");
//            transaction.setMsgType(msgType);
//            transaction.setReqPayload(xmlPayload);


            if (id == null) {
                freshTxns.add(tx); //  No valid txnId → goes to freshTransactions
//                transaction.setTarget("Fresh");
                freshTotal=freshTotal.add(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));
            } else {
                char ch = id.charAt(14);

                if (ch >= '0' && ch <= '4') {
                    Fc.add(tx);
//                    transaction.setTarget("FC");
                    fcTotal=fcTotal.add(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));
                } else if (ch >= '5' && ch <= '9') {
                    EPH.add(tx);
//                    transaction.setTarget("EPH");
                    ephTotal=ephTotal.add(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));

                }
            }

//            listOfTransactions.add(transaction);

        }


        String fcXml = null;
        String ephXml = null;

        boolean hasFC = !Fc.isEmpty();
        boolean hasEPH = !EPH.isEmpty();
        boolean hasFresh = !freshTxns.isEmpty();

        header.setTargetFC(hasFC);
        header.setTargetEPH(hasEPH);
        if (hasFC && hasEPH) {
            header.setTargetFCEPH(true);
        }

        if (hasFC && !hasEPH && !hasFresh) {

            fcXml = buildNewXml(originalDoc, dBuilder, Fc);
            header.setIntermediateReqFCCount(Fc.size());
            header.setConsolidateAmtFC(fcTotal);
            body.setFcPayload(fcXml);

        } else if (hasFC && !hasEPH && hasFresh) {

//            List<Node> combined = combine(Fc, freshTxns);
            Fc.addAll(freshTxns);
            fcTotal=fcTotal.add(freshTotal);
//            updateTargets(listOfTransactions, "Fresh", "FC");
            fcXml = buildNewXml(originalDoc, dBuilder, Fc);
            header.setIntermediateReqFCCount(Fc.size());
            header.setConsolidateAmtFC(fcTotal);
            body.setFcPayload(fcXml);


        } else if (!hasFC && hasEPH && !hasFresh) {

            ephXml = buildNewXml(originalDoc, dBuilder, EPH);
            header.setIntermediateReqEPHCount(EPH.size());
            header.setConsolidateAmtEPH(ephTotal);
            body.setEphPayload(ephXml);

        } else if (!hasFC && hasEPH && hasFresh) {

//            List<Node> combined = combine(EPH, freshTxns);
            EPH.addAll(freshTxns);
            ephTotal=ephTotal.add(freshTotal);
//            updateTargets(listOfTransactions, "Fresh", "EPH");
            ephXml = buildNewXml(originalDoc, dBuilder, EPH);
            header.setIntermediateReqEPHCount(EPH.size());
            header.setConsolidateAmtEPH(ephTotal);
            body.setEphPayload(ephXml);

        } else if (hasFC) {

            fcXml = buildNewXml(originalDoc, dBuilder, Fc);
            header.setIntermediateReqFCCount(Fc.size());
            header.setConsolidateAmtFC(fcTotal);
//            List<Node> ephCombined = combine(EPH, freshTxns);
            body.setFcPayload(fcXml);
            EPH.addAll(freshTxns);
            ephTotal=ephTotal.add(freshTotal);
//            updateTargets(listOfTransactions, "Fresh", "EPH");
            ephXml = buildNewXml(originalDoc, dBuilder, EPH);
            header.setIntermediateReqEPHCount(EPH.size());
            header.setConsolidateAmtEPH(ephTotal);
            body.setEphPayload(ephXml);

        }

        messageEventTracker.setHeader(header);
        messageEventTracker.setBody(body);

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageEventTracker);


//        if (fcXml != null) {
//            kafkaUtils.publishToResponseTopic(fcXml, fctopic);
//        }
//        if (ephXml != null) {
//            kafkaUtils.publishToResponseTopic(ephXml, ephtopic);
//        }


//        nilRepository.saveAllTransactionAudits(listOfTransactions);

    }

//    private String buildAndAudit(String xmlPayload, String msgId, String msgType, List<Node> txList, List<TransactionAudit> allTxns, String target, DocumentBuilder dBuilder, Document originalDoc) throws Exception {
//        String intermediateXml = buildNewXml(originalDoc, dBuilder, txList);
//        int count = (int) allTxns.stream().filter(t -> target.equals(t.getTarget())).count();
//        BigDecimal total = allTxns.stream().filter(t -> target.equals(t.getTarget())).map(TransactionAudit::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
//
//        MsgEventTracker tracker = new MsgEventTracker();
//        tracker.setMsgId(msgId);
//        tracker.setSource("NIL");
//        tracker.setTarget(target);
//        tracker.setFlowType("inward");
//        tracker.setMsgType(msgType);
//        tracker.setOrgnlReq(xmlPayload);
//        tracker.setIntermediateReq(intermediateXml);
//        tracker.setOrgnlReqCount(allTxns.size());
//        tracker.setIntermediateCount(count);
//        tracker.setConsolidateAmt(total);
//
//        nilRepository.saveDataInMsgEventTracker(tracker);
//        return intermediateXml;
//    }

//    private void updateTargets(List<TransactionAudit> transactions, String from, String to) {
//        transactions.stream().filter(t -> from.equalsIgnoreCase(t.getTarget())).forEach(t -> t.setTarget(to));
//    }
//
//    private List<Node> combine(List<Node> a, List<Node> b) {
//        List<Node> combined = new ArrayList<>(a);
//        combined.addAll(b);
//        return combined;
//    }


    private String extractTransactionIdentifier(Node tx, XPath xpath) throws XPathExpressionException {
        // Try InstrInf
        Node instrInfNode = (Node) xpath.evaluate(".//*[local-name()='InstrInf']", tx, XPathConstants.NODE);
        if (instrInfNode != null && instrInfNode.getTextContent() != null) {
            String text = instrInfNode.getTextContent().trim();
            String id = extractIdFromText(text);
            if (id != null) return id;
        }


        // Try all Ustrd
        NodeList ustrdNodes = (NodeList) xpath.evaluate(".//*[local-name()='Ustrd']", tx, XPathConstants.NODESET);
        for (int i = 0; i < ustrdNodes.getLength(); i++) {
            Node node = ustrdNodes.item(i);
            if (node != null && node.getTextContent() != null) {
                String text = node.getTextContent().trim();
                String id = extractIdFromText(text);
                if (id != null) return id;
            }
        }

        return null;
    }

    private String extractIdFromText(String text) {
        String[] tokens = text.split("[^A-Za-z0-9]"); // Split by non-alphanumeric characters

        for (String token : tokens) {
            token = token.trim();
            if ((token.startsWith("HDFCN") && token.length() == 22 && Character.isDigit(token.charAt(14)))) {
                return token;
            }
        }

        return null; // No valid ID found
    }


    private String buildNewXml(Document originalDoc, DocumentBuilder dBuilder, List<Node> txNodes) throws Exception {
        Document newDoc = dBuilder.newDocument();
        Node rootCopy = newDoc.importNode(originalDoc.getDocumentElement(), true);
        newDoc.appendChild(rootCopy);

        XPath xpath = XPathFactory.newInstance().newXPath();

        NodeList allTxs = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", newDoc, XPathConstants.NODESET);
        for (int i = allTxs.getLength() - 1; i >= 0; i--) {
            Node tx = allTxs.item(i);
            tx.getParentNode().removeChild(tx);
        }

        Node parentTxNode = (Node) xpath.evaluate("//*[local-name()='FIToFICstmrCdtTrf']", newDoc, XPathConstants.NODE);
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Node tx : txNodes) {
            Node importedTx = newDoc.importNode(tx, true);
            parentTxNode.appendChild(importedTx);

            Node amtNode = (Node) xpath.evaluate(".//*[local-name()='IntrBkSttlmAmt']", tx, XPathConstants.NODE);
            if (amtNode != null) {
                totalAmount = totalAmount.add(new BigDecimal(amtNode.getTextContent()));
            }
        }

        Node nbOfTxsNode = (Node) xpath.evaluate("//*[local-name()='NbOfTxs']", newDoc, XPathConstants.NODE);
        if (nbOfTxsNode != null) {
            nbOfTxsNode.setTextContent(String.valueOf(txNodes.size()));
        }

        Node ttlAmtNode = (Node) xpath.evaluate("//*[local-name()='TtlIntrBkSttlmAmt']", newDoc, XPathConstants.NODE);
        if (ttlAmtNode != null) {
            ttlAmtNode.setTextContent(totalAmount.toPlainString());
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(newDoc), new StreamResult(writer));
        return writer.toString();
    }
}

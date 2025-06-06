package com.hdfcbank.nilrouter.service.pacs008;

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

@Service
public class InwardService {

    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private KafkaUtils kafkaUtils;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Value("${topic.fctopic}")
    private String fcTopic;

    @Value("${topic.ephtopic}")
    private String ephTopic;

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


        MessageEventTracker messageEventTracker = new MessageEventTracker();

        Body body = new Body();
        body.setReqPayload(xmlPayload);

        Header header = new Header();

        header.setMsgId(msgId);
        header.setMsgType(msgType);
        header.setSource(Constants.NIL);
        header.setTargetEPH(ephPresent);
        header.setTargetFC(fcPresent);
        header.setFlowType(Constants.INWARD);
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

        String messageEventTrackerJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageEventTracker);


        kafkaUtils.publishToResponseTopic(messageEventTrackerJson, msgEventTrackerTopic);

        if(fcPresent)
        {
            kafkaUtils.publishToResponseTopic(messageEventTracker.getBody().getFcPayload(), fcTopic);

        }
        else
        {
            kafkaUtils.publishToResponseTopic(messageEventTracker.getBody().getEphPayload(), ephTopic);

        }


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
        BigDecimal fcTotal = BigDecimal.valueOf(0);
        BigDecimal ephTotal = BigDecimal.valueOf(0);
        BigDecimal freshTotal = BigDecimal.valueOf(0);


        MessageEventTracker messageEventTracker = new MessageEventTracker();

        Body body = new Body();
        body.setReqPayload(xmlPayload);

        Header header = new Header();
        header.setMsgType(utilityMethods.getMsgDefIdr(originalDoc));
        header.setMsgId(utilityMethods.getBizMsgIdr(originalDoc));
        header.setSource(Constants.NIL);
        header.setFlowType(Constants.INWARD);
        header.setOrignlReqCount(txNodes.getLength());
        header.setConsolidateAmt(utilityMethods.getTotalAmount(originalDoc));


        for (int i = 0; i < txNodes.getLength(); i++) {
            Node tx = txNodes.item(i);
            String id = extractTransactionIdentifier(tx, xpath);


            if (id == null) {
                freshTxns.add(tx); //  No valid txnId → goes to freshTransactions
                freshTotal = freshTotal.add(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));
            } else {
                char ch = id.charAt(14);

                if (ch >= '0' && ch <= '4') {
                    Fc.add(tx);
                    fcTotal = fcTotal.add(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));
                } else if (ch >= '5' && ch <= '9') {
                    EPH.add(tx);
                    ephTotal = ephTotal.add(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));

                }
            }


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

            Fc.addAll(freshTxns);
            fcTotal = fcTotal.add(freshTotal);
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

            EPH.addAll(freshTxns);
            ephTotal = ephTotal.add(freshTotal);
            ephXml = buildNewXml(originalDoc, dBuilder, EPH);
            header.setIntermediateReqEPHCount(EPH.size());
            header.setConsolidateAmtEPH(ephTotal);
            body.setEphPayload(ephXml);

        } else if (hasFC) {

            fcXml = buildNewXml(originalDoc, dBuilder, Fc);
            header.setIntermediateReqFCCount(Fc.size());
            header.setConsolidateAmtFC(fcTotal);
            body.setFcPayload(fcXml);
            EPH.addAll(freshTxns);
            ephTotal = ephTotal.add(freshTotal);
            ephXml = buildNewXml(originalDoc, dBuilder, EPH);
            header.setIntermediateReqEPHCount(EPH.size());
            header.setConsolidateAmtEPH(ephTotal);
            body.setEphPayload(ephXml);

        }

        messageEventTracker.setHeader(header);
        messageEventTracker.setBody(body);

        String messageEventTrackerJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageEventTracker);

        kafkaUtils.publishToResponseTopic(messageEventTrackerJson, msgEventTrackerTopic);

        if(hasFC)
        {
            kafkaUtils.publishToResponseTopic(messageEventTracker.getBody().getFcPayload(), fcTopic);

        }

        if(hasEPH)
        {
            kafkaUtils.publishToResponseTopic(messageEventTracker.getBody().getEphPayload(), ephTopic);

        }
    }

    public void processCugApproach(String xmlPayload) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        originalDoc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", originalDoc, XPathConstants.NODESET);
        String msgId = utilityMethods.getBizMsgIdr(originalDoc);
        String msgType = utilityMethods.getMsgDefIdr(originalDoc);
        BigDecimal totalAmount = utilityMethods.getTotalAmount(originalDoc);


        List<Node> Fc = new ArrayList<>();
        List<Node> EPH = new ArrayList<>();
        String fcXml = null;
        String ephXml = null;
        boolean fcPresent = false;
        boolean ephPresent = false;
        BigDecimal fcTotal = BigDecimal.valueOf(0);
        BigDecimal ephTotal = BigDecimal.valueOf(0);

        for (int i = 0; i < txNodes.getLength(); i++) {
            Node tx = txNodes.item(i);


            if (isValidCugAccount(tx)) {
                EPH.add(tx);
                ephPresent = true;
                ephTotal = ephTotal.add(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));
            } else {
                Fc.add(tx);
                fcPresent = true;
                fcTotal = fcTotal.add(new BigDecimal(utilityMethods.evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));

            }

        }

        MessageEventTracker messageEventTracker = new MessageEventTracker();

        Body body = new Body();
        body.setReqPayload(xmlPayload);

        Header header = new Header();

        header.setMsgId(msgId);
        header.setMsgType(msgType);
        header.setSource(Constants.NIL);
        header.setTargetEPH(ephPresent);
        header.setTargetFC(fcPresent);
        header.setFlowType(Constants.INWARD);
        header.setConsolidateAmt(totalAmount);
        header.setOrignlReqCount(txNodes.getLength());
        header.setIntermediateReqFCCount(Fc.size());
        header.setIntermediateReqEPHCount(EPH.size());
        header.setConsolidateAmtFC(fcTotal);
        header.setConsolidateAmtEPH(ephTotal);


        if (fcPresent) {
            fcXml = buildNewXml(originalDoc, dBuilder, Fc);
            body.setFcPayload(fcXml);
        }
        if (ephPresent) {
            ephXml = buildNewXml(originalDoc, dBuilder, EPH);
            body.setEphPayload(ephXml);
        }

        if (fcPresent && ephPresent) {
            header.setTargetFCEPH(true);
        }

        messageEventTracker.setHeader(header);
        messageEventTracker.setBody(body);

        String messageEventTrackerJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageEventTracker);

        kafkaUtils.publishToResponseTopic(messageEventTrackerJson, msgEventTrackerTopic);

        if(fcPresent)
        {
            kafkaUtils.publishToResponseTopic(messageEventTracker.getBody().getFcPayload(), fcTopic);

        }

        if(ephPresent)
        {
            kafkaUtils.publishToResponseTopic(messageEventTracker.getBody().getEphPayload(), ephTopic);

        }


    }

    private boolean isValidCugAccount(Node tx) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();

        String acctId = (String) xpath.evaluate(".//*[local-name()='CdtrAcct']/*[local-name()='Id']/*[local-name()='Othr']/*[local-name()='Id']", tx, XPathConstants.STRING);
        return nilRepository.cugAccountExists(acctId);

    }


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

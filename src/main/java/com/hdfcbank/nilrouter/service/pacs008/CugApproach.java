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
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CugApproach {

    @Autowired
    private KafkaUtils kafkaUtils;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    @Autowired
    private UtilityMethods utilityMethods;

    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private ObjectMapper objectMapper;

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


    }

    private boolean isValidCugAccount(Node tx) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();

        String acctId = (String) xpath.evaluate(".//*[local-name()='CdtrAcct']/*[local-name()='Id']/*[local-name()='Othr']/*[local-name()='Id']", tx, XPathConstants.STRING);
        return nilRepository.cugAccountExists(acctId);

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

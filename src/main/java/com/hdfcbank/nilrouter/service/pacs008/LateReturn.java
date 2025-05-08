package com.hdfcbank.nilrouter.service.pacs008;

import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.model.TransactionAudit;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class LateReturn {

    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private KafkaUtils kafkaUtils;

    public void splitXmlByTransactions(String xmlPayload) throws Exception {
        List<String> resultXmls = new ArrayList<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        originalDoc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();
        Node msgIdNode = (Node) xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", originalDoc, XPathConstants.NODE);
        String msgId = msgIdNode != null ? msgIdNode.getTextContent().trim() : null;
        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", originalDoc, XPathConstants.NODESET);

        List<Node> Fc = new ArrayList<>();
        List<Node> EPH = new ArrayList<>();
        List<Node> freshTxns = new ArrayList<>();
        List<TransactionAudit> listOfTransactions = new ArrayList<>();


        for (int i = 0; i < txNodes.getLength(); i++) {
            Node tx = txNodes.item(i);
            String id = extractTransactionIdentifier(tx, xpath);

            TransactionAudit transaction = new TransactionAudit();
            transaction.setMsgId(msgId);
            transaction.setEndToEndId(evaluateText(xpath, tx, ".//*[local-name()='EndToEndId']"));
            transaction.setTxnId(evaluateText(xpath, tx, ".//*[local-name()='TxId']"));
            transaction.setAmount(new BigDecimal(evaluateText(xpath, tx, ".//*[local-name()='IntrBkSttlmAmt']")));
            transaction.setBatchId(evaluateText(xpath, tx, ".//*[local-name()='RmtInf']//*[local-name()='Ustrd']"));
            transaction.setMsgType("pacs008");
            transaction.setSource("NIL");
            transaction.setFlowType("inward");
            transaction.setMsgType("pacs008");


            if (id == null) {
                freshTxns.add(tx); //  No valid txnId → goes to freshTransactions
                transaction.setTarget("Fresh");
            } else {
                char ch = id.charAt(14);

                if (ch >= '0' && ch <= '4') {
                    Fc.add(tx);
                    transaction.setTarget("FC");
                } else if (ch >= '5' && ch <= '9') {
                    EPH.add(tx);
                    transaction.setTarget("EPH");

                }
            }

            listOfTransactions.add(transaction);

        }


        String fcXml = null;
        String ephXml = null;

        boolean hasFC = !Fc.isEmpty();
        boolean hasEPH = !EPH.isEmpty();
        boolean hasFresh = !freshTxns.isEmpty();

        if (hasFC && !hasEPH && !hasFresh) {
            fcXml = buildNewXml(originalDoc, dBuilder, Fc);
        } else if (hasFC && !hasEPH && hasFresh) {
            fcXml = buildNewXml(originalDoc, dBuilder, combine(Fc, freshTxns));
            listOfTransactions.stream().filter(t -> "Fresh".equalsIgnoreCase(t.getTarget())).forEach(t -> t.setTarget("FC"));
        } else if (!hasFC && hasEPH && !hasFresh) {
            ephXml = buildNewXml(originalDoc, dBuilder, EPH);
        } else if (!hasFC && hasEPH && hasFresh) {
            ephXml = buildNewXml(originalDoc, dBuilder, combine(EPH, freshTxns));
            listOfTransactions.stream().filter(t -> "Fresh".equalsIgnoreCase(t.getTarget())).forEach(t -> t.setTarget("EPH"));
        } else if (hasFC) {
            fcXml = buildNewXml(originalDoc, dBuilder, Fc);
            ephXml = buildNewXml(originalDoc, dBuilder, combine(EPH, freshTxns));
            listOfTransactions.stream().filter(t -> "Fresh".equalsIgnoreCase(t.getTarget())).forEach(t -> t.setTarget("EPH"));
        }

        if (fcXml != null) {
            kafkaUtils.publishToResponseTopic(fcXml, "FCTOPIC");
        }
        if (ephXml != null) {
            kafkaUtils.publishToResponseTopic(ephXml, "EPHTOPIC");
        }


        if (msgId != null) {
            MsgEventTracker tracker = new MsgEventTracker();
            tracker.setMsgId(msgId);
            tracker.setSource("NIL");
            tracker.setTarget("FC");
            tracker.setFlowType("inward");
            tracker.setMsgType("pacs008");
            tracker.setOrgnlReq(xmlPayload);

           nilRepository.saveDataInMsgEventTracker(tracker);
        }

       nilRepository.saveAllTransactionAudits(listOfTransactions);

    }


    private List<Node> combine(List<Node> a, List<Node> b) {
        List<Node> combined = new ArrayList<>(a);
        combined.addAll(b);
        return combined;
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
            if ((token.startsWith("HDFCN") && token.length() == 22)) {
                return token;
            }
        }

        return null; // No valid ID found
    }

    private String evaluateText(XPath xpath, Node node, String expression) {
        try {
            Node result = (Node) xpath.evaluate(expression, node, XPathConstants.NODE);
            return result != null ? result.getTextContent().trim() : "";
        } catch (Exception e) {
            return "";
        }
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

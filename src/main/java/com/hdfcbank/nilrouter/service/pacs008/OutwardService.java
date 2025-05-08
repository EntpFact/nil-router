package com.hdfcbank.nilrouter.service.pacs008;

import com.hdfcbank.nilrouter.dao.NilRepository;
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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OutwardService {

    @Autowired
    private NilRepository nilRepository;

    public void auditForOutward(String xmlPayload) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        originalDoc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();
        Node msgIdNode = (Node) xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", originalDoc, XPathConstants.NODE);
        String msgId = msgIdNode != null ? msgIdNode.getTextContent().trim() : null;
        if (msgId != null) {
            MsgEventTracker tracker = new MsgEventTracker();
            tracker.setMsgId(msgId);
            tracker.setSource("NIL");
            tracker.setTarget("SFMS");
            tracker.setFlowType("outward");
            tracker.setMsgType("pacs008");
            tracker.setOrgnlReq(xmlPayload);

            nilRepository.saveDataInMsgEventTracker(tracker);
        }

        List<TransactionAudit> listOfTransactions = new ArrayList<>();

        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", originalDoc, XPathConstants.NODESET);
        for (int i = 0; i < txNodes.getLength(); i++) {
            Node txNode = txNodes.item(i);

            TransactionAudit transaction = new TransactionAudit();
            transaction.setMsgId(msgId);
            transaction.setEndToEndId(evaluateText(xpath, txNode, ".//*[local-name()='EndToEndId']"));
            transaction.setTxnId(evaluateText(xpath, txNode, ".//*[local-name()='TxId']"));
            transaction.setAmount(new BigDecimal(evaluateText(xpath, txNode, ".//*[local-name()='IntrBkSttlmAmt']")));
            transaction.setBatchId(evaluateText(xpath, txNode, ".//*[local-name()='RmtInf']//*[local-name()='Ustrd']"));
            transaction.setMsgType("pacs008");
            transaction.setSource("NIL");
            transaction.setTarget("SFMS");
            transaction.setFlowType("outward");

            listOfTransactions.add(transaction);

        }

        nilRepository.saveAllTransactionAudits(listOfTransactions);

    }

    private String evaluateText(XPath xpath, Node node, String expression) {
        try {
            Node result = (Node) xpath.evaluate(expression, node, XPathConstants.NODE);
            return result != null ? result.getTextContent().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }
}

package com.hdfcbank.nilrouter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.config.MessageXPathConfig;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.*;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuditService {
    private final MessageXPathConfig xPathConfig;

    public AuditService(MessageXPathConfig xPathConfig) {
        this.xPathConfig = xPathConfig;
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private UtilityMethods utilityMethods;

    @Autowired
    private KafkaUtils kafkaUtils;

    @Value("${topic.msgeventtrackertopic}")
    private String msgEventTrackerTopic;

    public void auditData(String xmlPayload) {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = null;
        Document originalDoc;
        String msgId, msgType = "";

        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
            originalDoc.getDocumentElement().normalize();


            // Extract message ID
            Node msgIdNode = (Node) xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", originalDoc, XPathConstants.NODE);
            msgId = msgIdNode != null ? msgIdNode.getTextContent().trim() : null;

            // Extract message type from Document element

            Node msgDefIdrNode = (Node) xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='MsgDefIdr']", originalDoc, XPathConstants.NODE);
            if (msgDefIdrNode != null) {
                msgType = msgDefIdrNode != null ? msgDefIdrNode.getTextContent().trim() : null;  // e.g., pacs008, camt059
            }

            MsgEventTracker tracker = new MsgEventTracker();
            if ("camt.052.001.08".equals(msgType) || "camt.054.001.08".equals(msgType)) {

                tracker.setMsgId(msgId);
                tracker.setSource("NIL");
                tracker.setTarget("FC & EPH");
                tracker.setFlowType("Inward");
                tracker.setMsgType(msgType);
                tracker.setOrgnlReq(xmlPayload);

            } else {
                tracker.setMsgId(msgId);
                tracker.setSource("NIL");
                tracker.setTarget("SFMS");
                tracker.setFlowType("Outward");
                tracker.setMsgType(msgType);
                tracker.setOrgnlReq(xmlPayload);
            }



            // Take the mappings from map
            Map<String, String> msgTypeToXPathMap = xPathConfig.getMappings();
            String txnXPath = msgTypeToXPathMap.get(msgType);
            if (txnXPath == null) {
                log.info("No transaction extraction logic defined for message type: " + msgType);
                return;
            }

            List<TransactionAudit> listOfTransactions = new ArrayList<>();
            NodeList txNodes = (NodeList) xpath.evaluate(txnXPath, originalDoc, XPathConstants.NODESET);
           if("pacs.004.001.10".equals(msgType)){
               tracker.setOrgnlReqCount(txNodes.getLength());

           }
            nilRepository.saveDataInMsgEventTracker(tracker);
            for (int i = 0; i < txNodes.getLength(); i++) {
                Node txNode = txNodes.item(i);

                TransactionAudit transaction = new TransactionAudit();
                transaction.setMsgId(msgId);

                if ("pacs.008.001.09".equals(msgType)) {
                    transaction.setEndToEndId(evaluateText(xpath, txNode, ".//*[local-name()='EndToEndId']"));
                    transaction.setTxnId(evaluateText(xpath, txNode, ".//*[local-name()='TxId']"));
                    transaction.setAmount(new BigDecimal(evaluateText(xpath, txNode, ".//*[local-name()='IntrBkSttlmAmt']")));
                    transaction.setBatchId(evaluateText(xpath, txNode, ".//*[local-name()='RmtInf']//*[local-name()='Ustrd']"));
                } else if ("camt.059.001.06".equals(msgType)) {
                    transaction.setEndToEndId(evaluateText(xpath, txNode, ".//*[local-name()='OrgnlEndToEndId']"));
                    transaction.setTxnId(evaluateText(xpath, txNode, ".//*[local-name()='OrgnlItmId']"));
                    transaction.setAmount(new BigDecimal(evaluateText(xpath, txNode, ".//*[local-name()='Amt']")));
                    transaction.setBatchId(evaluateText(xpath, txNode, ".//*[local-name()='AddtlNtfctnInf']"));
                } else if ("pacs.004.001.10".equals(msgType)) {
                    transaction.setEndToEndId(evaluateText(xpath, txNode, ".//*[local-name()='OrgnlEndToEndId']"));
                    transaction.setTxnId(evaluateText(xpath, txNode, ".//*[local-name()='OrgnlTxId']"));
                    transaction.setAmount(new BigDecimal(evaluateText(xpath, txNode, ".//*[local-name()='RtrdIntrBkSttlmAmt']")));
                    transaction.setBatchId(evaluateText(xpath, txNode, ".//*[local-name()='RmtInf']//*[local-name()='Ustrd']"));

                }

                transaction.setMsgType(msgType);
                transaction.setSource("NIL");
                transaction.setTarget("SFMS");
                transaction.setFlowType("Outward");
                transaction.setReqPayload(xmlPayload);

                listOfTransactions.add(transaction);
            }

            nilRepository.saveAllTransactionAudits(listOfTransactions);

        } catch (ParserConfigurationException | XPathExpressionException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        } catch (SAXException e) {
            log.error(e.toString());
        }
    }


    public void constructOutwardJsonAndPublish(String xmlPayload) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlPayload)));
        originalDoc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList txNodes = (NodeList) xpath.evaluate("//*[local-name()='CdtTrfTxInf']", originalDoc, XPathConstants.NODESET);


        MessageEventTracker messageEventTracker = new MessageEventTracker();
        Header header = new Header();
        Body body = new Body();
        header.setMsgId(utilityMethods.getBizMsgIdr(originalDoc));
        header.setSource("NIL");
        header.setMsgType(utilityMethods.getMsgDefIdr(originalDoc));
        header.setBatchId("Batch 10");
        header.setConsolidateAmt(utilityMethods.getTotalAmount(originalDoc));
        header.setFlowType("Outward");
        header.setTargetSFMS(true);
        header.setOrignlReqCount(txNodes.getLength());

        body.setReqPayload(xmlPayload);

        messageEventTracker.setBody(body);
        messageEventTracker.setHeader(header);

        String messageEventTrackerJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messageEventTracker);

        kafkaUtils.publishToResponseTopic(messageEventTrackerJson, msgEventTrackerTopic);

    }

    private String evaluateText(XPath xpath, Node node, String expression) {
        Node result = null;
        try {
            result = (Node) xpath.evaluate(expression, node, XPathConstants.NODE);

        } catch (Exception e) {
            log.error(e.toString());
        }
        return result != null ? result.getTextContent().trim() : "";
    }


}

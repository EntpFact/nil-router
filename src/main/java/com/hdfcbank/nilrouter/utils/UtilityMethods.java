package com.hdfcbank.nilrouter.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
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

@Slf4j
@Component
public class UtilityMethods {

    public String getBizMsgIdr(Document originalDoc) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node msgIdNode = (Node) xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", originalDoc, XPathConstants.NODE);
        String msgId = msgIdNode != null ? msgIdNode.getTextContent().trim() : null;
        return msgId;
    }

    public String getMsgDefIdr(Document originalDoc) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node msgIdNode = (Node) xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='MsgDefIdr']", originalDoc, XPathConstants.NODE);
        String msgId = msgIdNode != null ? msgIdNode.getTextContent().trim() : null;
        return msgId;

    }



    public BigDecimal getTotalAmount(Document originalDoc) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        String totalAmountString = (String) xpath.evaluate("//*[local-name()='GrpHdr']/*[local-name()='TtlIntrBkSttlmAmt']", originalDoc, XPathConstants.STRING);
        BigDecimal totalAmount = new BigDecimal(totalAmountString);
        return totalAmount;

    }

    public String evaluateText(XPath xpath, Node node, String expression) {
        try {
            Node result = (Node) xpath.evaluate(expression, node, XPathConstants.NODE);
            return result != null ? result.getTextContent().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isOutward(String xml) {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new InputSource(new StringReader(xml)));

            String fromMmbId = doc.getElementsByTagName("Fr").item(0)
                    .getTextContent().trim().toUpperCase();

            String toMmbId = doc.getElementsByTagName("To").item(0)
                    .getTextContent().trim().toUpperCase();
            if (fromMmbId.contains("HDFC") && toMmbId.contains("RBI")) {
                // Outward flow
                return true;
            }

        } catch (ParserConfigurationException e) {
            log.error(e.toString());
        } catch (IOException e) {
            log.error(e.toString());
        } catch (SAXException e) {
            log.error(e.toString());
        }

        return false;
    }

}

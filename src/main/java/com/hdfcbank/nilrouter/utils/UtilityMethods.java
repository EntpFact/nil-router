package com.hdfcbank.nilrouter.utils;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

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

}

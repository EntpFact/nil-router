package com.hdfcbank.nilrouter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.nilrouter.config.MsgXPathConfig;
import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.Body;
import com.hdfcbank.nilrouter.model.Header;
import com.hdfcbank.nilrouter.model.MessageEventTracker;
import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
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

@Slf4j
@Service
public class AuditService {



    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private UtilityMethods utilityMethods;


    public MsgEventTracker auditIncomingMessage(String xml) {
        MsgEventTracker msgEventTracker = new MsgEventTracker();
        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            msgEventTracker.setMsgId(utilityMethods.getBizMsgIdr(document));
            msgEventTracker.setOrgnlReq(xml);

            nilRepository.saveDataInMsgEventTracker(msgEventTracker);

        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            throw new RuntimeException(e);
        }
        return msgEventTracker;
    }
}

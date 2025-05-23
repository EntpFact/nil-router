package com.hdfcbank.nilrouter.service.admi;

import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

@Service
public class AdmiXmlProcessor {

    @Autowired
    UtilityMethods utilityMethods;

    @Autowired
    private NilRepository nilRepository;

    @Autowired
    private KafkaUtils kafkaUtils;

    @Value("${topic.fctopic}")
    private String fctopic;

    @Value("${topic.ephtopic}")
    private String ephtopic;

    @ServiceActivator(inputChannel = "admi004")
    public void parseXml(String xmlString) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document originalDoc = dBuilder.parse(new InputSource(new StringReader(xmlString)));
        originalDoc.getDocumentElement().normalize();
        XPath xpath = XPathFactory.newInstance().newXPath();

        String msgId = utilityMethods.getMsgDefIdr(originalDoc);
        String CreDt = xpath.evaluate("//*[local-name()='AppHdr']/*[local-name()='CreDt']", originalDoc);
        String evtCd = xpath.evaluate("//*[local-name()='Document']//*[local-name()='EvtCd']", originalDoc);

        MsgEventTracker tracker = new MsgEventTracker();
        tracker.setMsgId(msgId);
        tracker.setSource("NIL");
        tracker.setFlowType("inward");
        tracker.setMsgType(evtCd);
        tracker.setOrgnlReq(xmlString);

        if (evtCd.equalsIgnoreCase("F95")) {
            tracker.setTarget("FC & EPH");
            kafkaUtils.publishToResponseTopic(xmlString, fctopic);
            kafkaUtils.publishToResponseTopic(xmlString, ephtopic);


        } else {

            char ch = msgId.charAt(13);
            if (ch >= '0' && ch <= '4') {
                tracker.setTarget("FC");
                kafkaUtils.publishToResponseTopic(xmlString, fctopic);

            } else if (ch >= '5' && ch <= '9') {
                tracker.setTarget("EPH");
                kafkaUtils.publishToResponseTopic(xmlString, ephtopic);

            }
        }

        nilRepository.saveDataInMsgEventTracker(tracker);


    }
}

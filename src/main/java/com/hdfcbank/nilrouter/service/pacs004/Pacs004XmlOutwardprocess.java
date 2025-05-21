package com.hdfcbank.nilrouter.service.pacs004;

import com.hdfcbank.nilrouter.dao.NilRepository;
import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.model.MsgEventTracker;
import com.hdfcbank.nilrouter.model.Pacs004Fields;
import com.hdfcbank.nilrouter.model.TransactionAudit;
import com.hdfcbank.nilrouter.utils.UtilityMethods;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class Pacs004XmlOutwardprocess {

    @Autowired
    NilRepository dao;

    @Value("${topic.sfmstopic}")
    private String sfmstopic;

    @Autowired
    UtilityMethods utilityMethods;
    @Autowired
    KafkaUtils kafkautils;


    public void processXML(String xml) {
        List<Pacs004Fields> pacs004 = new ArrayList<>();
        String bizMsgIdr = null, orgnlItmId = null, orgnlEndToEndId = null;
        try {

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document document = builder.parse(new InputSource(new StringReader(xml)));

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            bizMsgIdr = xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='AppHdr']/*[local-name()='BizMsgIdr']", document);


            NodeList orgnlItmAndStsList = (NodeList) xpath.evaluate("/*[local-name()='RequestPayload']/*[local-name()='Document']//*[local-name()='TxInf']", document, XPathConstants.NODESET);

            double consolidateAmountSFMS =0;
            for (int i = 0; i < orgnlItmAndStsList.getLength(); i++) {
                Element orgnlItmAndSts = (Element) orgnlItmAndStsList.item(i);
                String batchIdValue = "";
                orgnlItmId = xpath.evaluate("./*[local-name()='OrgnlTxId']", orgnlItmAndSts);
                orgnlEndToEndId = xpath.evaluate("./*[local-name()='OrgnlEndToEndId']", orgnlItmAndSts);
                String amount =xpath.evaluate("./*[local-name()='RtrdIntrBkSttlmAmt']", orgnlItmAndSts);

                NodeList batchIdList=(NodeList)xpath.evaluate(".//*[local-name()='OrgnlTxRef']/*[local-name()='UndrlygCstmrCdtTrf']//*[local-name()='RmtInf']",  orgnlItmAndSts,XPathConstants.NODE);
                for(int j=0;j<batchIdList.getLength();j++){
                    String batchId=(batchIdList.item(j)).getTextContent();
                    if(batchId.contains("BatchId")){
                        batchIdValue=batchId;
                    }

                }
                consolidateAmountSFMS +=Double.parseDouble(amount);
                pacs004.add(new Pacs004Fields(bizMsgIdr, orgnlEndToEndId,orgnlItmId, amount, "SFMS",batchIdValue));

            }


                MsgEventTracker tracker = new MsgEventTracker();
                tracker.setMsgId(utilityMethods.getBizMsgIdr(document));
                tracker.setSource("NIL");
                tracker.setTarget("SFMS");
                tracker.setFlowType("Outward");

                tracker.setIntermediateReq("XML");
                tracker.setIntermediateCount(orgnlItmAndStsList.getLength());
                tracker.setOrgnlReqCount(orgnlItmAndStsList.getLength());
                tracker.setConsolidateAmt(BigDecimal.valueOf(consolidateAmountSFMS));
                tracker.setMsgType(utilityMethods.getMsgDefIdr(document));
                tracker.setOrgnlReq(xml);

                dao.saveDataInMsgEventTracker(tracker);

            List<TransactionAudit> transactionAudits = extractPacs004Transactions(document, xml,pacs004);

            dao.saveAllTransactionAudits(transactionAudits);
            String outputDocString = documentToXml(document);
            log.info("SFMS  : {}", outputDocString);

          //  kafkautils.publishToResponseTopic(xml,sfmstopic);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<TransactionAudit> extractPacs004Transactions(Document originalDoc, String xml, List<Pacs004Fields> pacs004FieldsFields) throws XPathExpressionException {
        List<TransactionAudit> listOfTransactions = new ArrayList<>();

        String msgId = utilityMethods.getMsgDefIdr(originalDoc);

        for (Pacs004Fields pacs004 : pacs004FieldsFields) {

            TransactionAudit transaction = new TransactionAudit();
            transaction.setMsgId(utilityMethods.getBizMsgIdr(originalDoc));
            transaction.setEndToEndId(pacs004.getEndToEndId());
            transaction.setTxnId(pacs004.getTxId());
            transaction.setMsgType(msgId);
            transaction.setSource("NIL");
            transaction.setBatchId(pacs004.getBatchId());
            double d=Double.parseDouble(pacs004.getAmount());
            transaction.setAmount(BigDecimal.valueOf(d));
            transaction.setTarget("SFMS");
            transaction.setFlowType("Outward");
            transaction.setReqPayload(xml);

            listOfTransactions.add(transaction);
        }
        return listOfTransactions;
    }



    public String documentToXml(Document doc) throws TransformerException {
        StringWriter writer = new StringWriter();
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        // Perform the transformation
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }


    private static Element createElementNS(Document doc, String localName) {
        final String PACS_NS = "urn:iso:std:iso:20022:tech:xsd:pacs.004.001.10";
        return doc.createElementNS(PACS_NS, localName);
    }

    private static int extractEndToEndIdDigit(String endToEndId) {
      /*  Pattern pattern = Pattern.compile("/XUTR/HDFCH(\\d{9})");
        Matcher matcher = pattern.matcher(endToEndId);

        if (matcher.find()) {
            return Character.getNumericValue(matcher.group(1).charAt(0));
        }*/

        Pattern pattern = Pattern.compile("^.{14}(.)"); // 14 characters, then capture the 15th
        Matcher matcher = pattern.matcher(endToEndId);

        if (matcher.find()) {
            return Character.getNumericValue(matcher.group(1).charAt(0));
        }
        return -1;
    }

}

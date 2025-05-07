package com.hdfcbank.nilrouter.service.camt;


import com.hdfcbank.nilrouter.kafkaproducer.KafkaUtils;
import com.hdfcbank.nilrouter.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;


@Service
public class CamtXmlProcessor {

    @Value("${topic.fctopic}")
    private String fctopic;

    @Value("${topic.ephtopic}")
    private String ephtopic;


    @Autowired
    AuditService auditService;

    @Autowired
    KafkaUtils kafkaUtils;

    @ServiceActivator(inputChannel = "camt_52_54")
    public void parseXml(String xmlString) {

        auditService.auditData(xmlString);
        kafkaUtils.publishToResponseTopic(xmlString, fctopic);
        kafkaUtils.publishToResponseTopic(xmlString, ephtopic);
    }
}





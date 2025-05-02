package com.hdfcbank.nilrouter.service.pacs008;


import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;


@Service
public class Pacs008XmlProcessor {

   @ServiceActivator(inputChannel = "pacs008")
    public  void parseXml(String xmlString) {
        System.out.println("In Pacs008XmlProcessor : "+xmlString);
    }


}





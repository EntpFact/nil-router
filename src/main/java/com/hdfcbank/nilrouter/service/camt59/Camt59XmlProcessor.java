package com.hdfcbank.nilrouter.service.camt59;


import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;


@Service
public class Camt59XmlProcessor {

    @ServiceActivator(inputChannel = "camt59")
    public void processXML(String xml) {

        System.out.println("In Camt59XmlProcessor : "+xml);
    }
}


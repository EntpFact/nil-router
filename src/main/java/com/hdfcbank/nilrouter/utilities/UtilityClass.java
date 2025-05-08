package com.hdfcbank.nilrouter.utilities;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

public final class UtilityClass {
    private UtilityClass(){}

    public static boolean isOutward(String xml) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(new InputSource(new StringReader(xml)));

        String fromMmbId = doc.getElementsByTagName("Fr").item(0)
                .getTextContent().trim().toUpperCase();

        String toMmbId = doc.getElementsByTagName("To").item(0)
                .getTextContent().trim().toUpperCase();
        if (fromMmbId.contains("HDFC") && toMmbId.contains("RBI")) {
            // Outward flow
            return true;
        }
        return false;

    }

}

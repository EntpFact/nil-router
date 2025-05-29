package com.hdfcbank.nilrouter.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hdfcbank.nilrouter.exception.NILException;
import com.hdfcbank.nilrouter.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import reactor.core.publisher.Mono;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
public class ProcessController {

    @Autowired
    private MessageChannel routingChannel;

    @CrossOrigin
    @GetMapping(path = "/healthz")
    public ResponseEntity<?> healthz() {
        return new ResponseEntity<>("Success", HttpStatus.OK);
    }

    @CrossOrigin
    @GetMapping(path = "/ready")
    public ResponseEntity<?> ready() {
        return new ResponseEntity<>("Success", HttpStatus.OK);
    }


    @CrossOrigin
    @PostMapping("/process")
    public Mono<ResponseEntity<Response>> process(@RequestBody Map<String, Object> cloudEvent) throws JsonProcessingException {
        log.info("....Processing Started.... ");
        return Mono.fromCallable(() -> {
            Map<String, String> payloadMap = new HashMap<>();
            try {
                // Get base64 encoded data
                String xmlString = validateXml(cloudEvent);

                // Process the XML message
                routingChannel.send(MessageBuilder.withPayload(xmlString).build()
                );

                return ResponseEntity.ok(new Response("SUCCESS", "Message Processed."));
            } catch (Exception ex) {
                log.error("Failed in consuming the message: {}", ex);
                throw new NILException("Failed in consuming the message", ex);
            } finally {
                log.info("....Processing Completed.... ");
            }
        }).onErrorResume(ex -> {
            return Mono.just(new ResponseEntity<>(new Response("ERROR", "Message Processing Failed"), HttpStatus.INTERNAL_SERVER_ERROR));
        });
    }


    private String validateXml(Map<String, Object> cloudEvent) {
        String base64Data = (String) cloudEvent.get("data_base64");

        // Decode base64 data to XML message
        String xmlMessage = new String(Base64.getDecoder().decode(base64Data), StandardCharsets.UTF_8);

        // Remove BOM if exists and trim any extra whitespace
        xmlMessage = removeBOM(xmlMessage);
        xmlMessage = xmlMessage.trim();

        // Log the decoded XML message for debugging
        log.info("Decoded XML: {}", xmlMessage);

        // Now you can process the raw XML string
        String xmlString = "";  // String to store the converted XML
        try {
            // Parse the XML string to a Document
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlMessage.getBytes()));

            // Convert Document to string using Transformer
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(new DOMSource(document), result);

            // Assign the XML as a string to the variable
            xmlString = writer.toString();
        } catch (Exception e) {
            e.printStackTrace();
            xmlString = "Error processing XML";  // If there's an error, assign an error message
        }
        return xmlString;
    }

    private String removeBOM(String xml) {
        // Check for UTF-8 BOM (EF BB BF)
        if (xml != null && xml.startsWith("\uFEFF")) {
            return xml.substring(1);
        }
        // Sometimes BOM shows up as a junk character like �, remove non-printables too
        return xml.replaceAll("^[^\\x20-\\x7E<]+", "");  // Removes any non-XML leading junk
    }


    @CrossOrigin
    @PostMapping("/testProcess")
    public Mono<ResponseEntity<Response>> testProcess(@RequestBody String request) throws JsonProcessingException {

        log.info("....Processing Started.... ");

        return Mono.fromCallable(() -> {
            Map<String, String> payloadMap = new HashMap<>();
            try {
                String xmlMessage = request;
                //log.info("Request data--{}", xmlMessage);
                routingChannel.send(MessageBuilder.withPayload(request).build());

                return ResponseEntity.ok(new Response("SUCCESS", "Message Processed."));
            } catch (Exception ex) {
                log.error("Failed in consuming the message: {}", ex);

                throw new NILException("Failed in consuming the message", ex);
            } finally {
                log.info("....Processing Completed.... ");
            }
        }).onErrorResume(ex -> {
            return Mono.just(new ResponseEntity<>(new Response("ERROR", "Message Processing Failed"), HttpStatus.INTERNAL_SERVER_ERROR));
        });
    }


}

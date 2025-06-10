package com.hdfcbank.nilrouter.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.router.HeaderValueRouter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

@Slf4j
@Configuration
public class NilMsgRoutingConfig {

    private final MsgMappings msgMappings;

    @Autowired
    public NilMsgRoutingConfig(MsgMappings msgMappings) {
        this.msgMappings = msgMappings;
    }

    @Bean
    public MessageChannel routingChannel() {
        return new DirectChannel();
    }

    /*
     * Without a QueueChannel (or some other PollableChannel) for replies,
     *  your gateway would have no channel to receive the handler’s return value on,
     *  and your call to gateway.route(xml) would never complete
     * */
    @Bean
    public PollableChannel replyChannel() {
        return new QueueChannel();
    }

    @Bean
    public MessageChannel channelA() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel camt59() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel pacs008() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel pacs002() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel pacs004() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel admi004() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel camt_52_54() {
        return new DirectChannel();
    }

    /**
     * Integration flow: enrich header using determineHeaderValue, then route
     */

    @Bean
    public IntegrationFlow routerFlow() {
        return IntegrationFlow.from("routingChannel")
                .enrichHeaders(h -> h.headerFunction("testHeader", this::determineHeaderValue))
                .route(router())
                .get();
    }

    /**
     * Extracts the header value by parsing the XML for the tag named msgMappings.getHeader()
     */
    private String determineHeaderValue(Message<?> message) {
        String msgDefIdr = null;
        try {
            String payload = message.getPayload().toString();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // Parse XML from String
            Document document = builder.parse(new InputSource(new StringReader(payload)));


            NodeList msgDefIdrList = document.getElementsByTagName("MsgDefIdr");
            if (msgDefIdrList.getLength() > 0) {
                msgDefIdr = msgDefIdrList.item(0).getTextContent();
                log.info("Message Definition Identifier: " + msgDefIdr);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return msgDefIdr;
    }

    /**
     * HeaderValueRouter uses header from properties and mappings
     */
    @Bean
    public HeaderValueRouter router() {
        HeaderValueRouter router = new HeaderValueRouter(msgMappings.getHeader());
        msgMappings.getMappings().forEach((key, channel) ->
                router.setChannelMapping(key, channel)
        );
        // Set the default output channel to the actual bean, not its name
        router.setDefaultOutputChannel(replyChannel());
        return router;
    }

}
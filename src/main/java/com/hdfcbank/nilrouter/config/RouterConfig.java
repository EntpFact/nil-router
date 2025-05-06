package com.hdfcbank.nilrouter.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.router.HeaderValueRouter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

@Slf4j
@Configuration
public class RouterConfig {

    private final RoutingProperties routingProperties;

    public RouterConfig(RoutingProperties routingProperties) {
        this.routingProperties = routingProperties;
    }

    @Bean
    public MessageChannel routingChannel() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    public MessageChannel channelA() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    public MessageChannel camt59() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    public MessageChannel pacs008() {
        return MessageChannels.direct().getObject();
    }

    @Bean
    public IntegrationFlow routerFlow() {
        return IntegrationFlow.from("routingChannel")
                .enrichHeaders(h -> h.headerFunction("testHeader", this::determineHeaderValue))
                .route(router())
                .get();
    }

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

    @Bean
    public HeaderValueRouter router() {
        HeaderValueRouter router = new HeaderValueRouter(routingProperties.getHeader());
        routingProperties.getMappings().forEach(router::setChannelMapping);
        return router;
    }

    @Bean
    @ServiceActivator(inputChannel = "channelA")
    public MessageHandler handlerA() {
        return message -> log.info("Handled by channelA: " + message.getPayload());
    }


}
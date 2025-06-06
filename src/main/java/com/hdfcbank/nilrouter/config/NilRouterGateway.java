package com.hdfcbank.nilrouter.config;

import org.springframework.integration.annotation.MessagingGateway;

import java.util.Map;

/**
 * Gateway for request-reply to route XML and receive Map response
 */
@MessagingGateway(defaultRequestChannel = "routingChannel", defaultReplyChannel = "replyChannel")
interface NilRouterGateway {
    /**
     * The `route(String xml)` method is implemented at runtime by Spring Integration.
     * Internally, this gateway sends the XML payload to the 'routingChannel',
     * and awaits a reply on the 'replyChannel'. The router flow defined by
     *
     * @Router and @ServiceActivator annotations handles the routing and processing,
     * returning a Map<String, String> response.
     */
    Map<String, String> route(String xml);
}


package com.hdfcbank.nilrouter.config;


import com.hdfcbank.nilrouter.service.AuditService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
@Slf4j
public class KafkastreamsConfig {

    @Autowired
    private NilRouterGateway gateway;

    @Autowired
    AuditService auditService;

    @Autowired
    Environment env;

    @PostConstruct
    public void logKafkaBroker() {
        System.out.println("Effective Kafka broker: " + env.getProperty("spring.cloud.stream.kafka.streams.binder.brokers"));
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @Bean
    public Function<KStream<String, String>, KStream<String, String>[]> processStream() {
        return input -> {
            log.info("Kakfa Streaming Started");
            KStream<String, String> output = input.flatMap((key, xml) -> {


                // Audit Incoming message
                auditService.auditIncomingMessage(xml);
                // Process the XML message
                Map<String, String> response = gateway.route(xml);
                log.info("Response {}", response);
                List<KeyValue<String, String>> results = new ArrayList<>();
                response.forEach((swtch, xmlString) -> results.add(KeyValue.pair(swtch, xmlString)));
                log.info("Kakfa Streaming completed");
                return results;
            });
            return output.branch((key, val) -> key.equalsIgnoreCase("FC"), (key, val) -> key.equalsIgnoreCase("EPH"),
                    (key, val) -> key.equalsIgnoreCase("SFMS"), (key, val) -> key.equalsIgnoreCase("MET"));
        };
    }
}

package com.hdfcbank.nilrouter.mq;

import com.hdfcbank.nilrouter.config.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class MQService
{
    @Autowired
    JmsTemplate jmsTemplate;
    @Autowired
    RetryConfig retryConfig;
    @Autowired
    RetryTemplate retryTemplate;

    public String sendMessageToMq(String message) {

       retryTemplate.execute(context->{
        try {

            jmsTemplate.convertAndSend("DEV.QUEUE.2", message);
            log.info("Message sent successfully");
            return null;
        } catch (JmsException e) {

            System.err.println("Failed to send message, attempt " + context.getRetryCount());
            e.printStackTrace();
            throw e;
        }

       });
        return "Message sent successfully!";
    }
}

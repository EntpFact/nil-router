package com.hdfcbank.nilrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@ComponentScan(basePackages = {
        "com.hdfcbank"
})
public class NilRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(NilRouterApplication.class, args);
    }

}

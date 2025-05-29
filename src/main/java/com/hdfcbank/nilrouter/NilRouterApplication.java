package com.hdfcbank.nilrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.hdfcbank"
})
public class NilRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(NilRouterApplication.class, args);
    }

}

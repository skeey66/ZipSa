package com.zipsa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ZipsaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZipsaApplication.class, args);
    }
}

package com.spherecast.agnes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan("com.spherecast.agnes.config")
public class AgnesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgnesApplication.class, args);
    }
}

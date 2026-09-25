package com.classmgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ClassMemberApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClassMemberApplication.class, args);
    }
}

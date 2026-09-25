package com.classmgmt.config;

import com.classmgmt.service.StudentService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeedConfig {

    @Bean
    public ApplicationRunner seedRunner(StudentService studentService) {
        return args -> studentService.seedIfEmpty();
    }
}

package com.classmgmt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SchemaFixConfig {

    private static final Logger log = LoggerFactory.getLogger(SchemaFixConfig.class);

    @Bean
    public ApplicationRunner fixCheckRecordTextColumns(JdbcTemplate jdbc) {
        return args -> {
            try {
                jdbc.execute("""
                        ALTER TABLE check_record
                          MODIFY participated_json LONGTEXT NULL,
                          MODIFY absent_json LONGTEXT NULL,
                          MODIFY invalid_json LONGTEXT NULL,
                          MODIFY duplicates_json LONGTEXT NULL,
                          MODIFY raw_text LONGTEXT NULL
                        """);
                log.info("Ensured check_record text columns are LONGTEXT");
            } catch (Exception e) {
                log.warn("Could not widen check_record columns: {}", e.getMessage());
            }
        };
    }
}

/**
 * Objetivo: Clase de arranque de la aplicaciÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â³n Spring Boot.
 */
package com.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.assistant",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.assistant\\.core\\..*"
    )
)
@EntityScan(basePackages = "com.assistant.domain.model")
@EnableKafka
@EnableAsync
public class AssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }
}





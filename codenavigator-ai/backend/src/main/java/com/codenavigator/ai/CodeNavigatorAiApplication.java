package com.codenavigator.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for CodeNavigator AI backend.
 *
 * @SpringBootApplication is a composite of:
 *   - @Configuration      -> this class can define Spring beans
 *   - @EnableAutoConfiguration -> Spring Boot configures beans based on classpath (e.g. DataSource
 *                                  because mysql-connector-j + spring-data-jpa are present)
 *   - @ComponentScan      -> scans com.codenavigator.ai and all sub-packages for
 *                             @Component/@Service/@Repository/@Controller beans
 */
@SpringBootApplication
@EnableAsync
public class CodeNavigatorAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeNavigatorAiApplication.class, args);
    }

}

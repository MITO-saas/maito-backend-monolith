package com.maito.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 / Swagger documentation configuration bean.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI maitoOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Maito E-Commerce Monolith API")
                .description("Production-grade Modular Monolith API for Maito Makhana Products, Orders, and Payments")
                .version("v1.0.0")
                .contact(new Contact()
                    .name("Maito Engineering Architecture")
                    .email("architecture@maito.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}

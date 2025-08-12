package com.newsagent.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI newsAgentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("News Agent API")
                        .description("Stock news aggregation and scoring API")
                        .version("0.1.0"))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8000")
                                .description("Local development server")
                ));
    }
}
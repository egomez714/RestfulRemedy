package com.restfulremedy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "anthropic")
@Getter
@Setter
public class AnthropicConfig {

    private String apiKey;
    private String model;

    @Bean
    public RestClient anthropicRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }
}

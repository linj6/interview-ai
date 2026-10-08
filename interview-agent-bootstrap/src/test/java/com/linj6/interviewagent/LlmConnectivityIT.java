package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class LlmConnectivityIT {

    @Test
    void receivesNonEmptyResponseFromDashScope() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SmokeTestApplication.class)
                .profiles("local")
                .web(WebApplicationType.NONE)
                .properties(
                        "app.ai.real-llm-enabled=true",
                        "spring.autoconfigure.exclude="
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
                .run()) {
            ChatClient.Builder chatClientBuilder = context.getBean(ChatClient.Builder.class);

            String response = chatClientBuilder
                    .build()
                    .prompt("Reply with a short acknowledgement that the connection works.")
                    .call()
                    .content();

            assertThat(response).isNotBlank();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class SmokeTestApplication {}
}

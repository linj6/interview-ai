package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.UnavailableInterviewAiApi;
import com.linj6.interviewagent.ai.api.InterviewAiApi;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class OpeningAiBeanSmokeIT {

    @Test
    void localEnvironmentRegistersDashScopeOpeningAiApi() {
        assertRealLlmBean("local");
    }

    @Test
    void prodEnvironmentRegistersDashScopeOpeningAiApi() {
        assertRealLlmBean("prod");
    }

    private void assertRealLlmBean(String environmentProfile) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SmokeTestApplication.class)
                .profiles(environmentProfile)
                .web(WebApplicationType.NONE)
                .properties("app.ai.real-llm-enabled=true", "spring.ai.dashscope.api-key=smoke-test-placeholder")
                .run()) {
            assertThat(context.getBean(ChatModel.class)).isNotNull();
            assertThat(context.getBean(ChatClient.Builder.class)).isNotNull();
            assertThat(context.getBean(InterviewAiApi.class)).isInstanceOf(InterviewAiService.class);
            assertThat(context.getBeansOfType(UnavailableInterviewAiApi.class)).isEmpty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({InterviewAiService.class, UnavailableInterviewAiApi.class})
    static class SmokeTestApplication {}
}

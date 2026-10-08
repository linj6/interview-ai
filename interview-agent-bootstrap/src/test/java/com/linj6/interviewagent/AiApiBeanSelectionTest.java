package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.UnavailableInterviewAiApi;
import com.linj6.interviewagent.ai.api.InterviewAiApi;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AiApiBeanSelectionTest {

    @Test
    void defaultProfileRegistersUnavailableAiApi() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(InterviewAiService.class, UnavailableInterviewAiApi.class);
            context.refresh();

            assertThat(context.getBean(InterviewAiApi.class)).isInstanceOf(UnavailableInterviewAiApi.class);
            assertThat(context.getBeansOfType(InterviewAiApi.class)).hasSize(1);
        }
    }
}

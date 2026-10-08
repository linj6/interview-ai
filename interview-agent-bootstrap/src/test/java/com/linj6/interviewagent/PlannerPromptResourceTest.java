package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.api.planner.PlannerContext;
import com.linj6.interviewagent.ai.planner.PlannerProposal;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class PlannerPromptResourceTest {
    @Test
    void initializesPlannerResourceOnceAndPassesInjectedResourceOnEveryCall() {
        var builder = mock(ChatClient.Builder.class);
        var client = mock(ChatClient.class);
        var request = mock(ChatClient.ChatClientRequestSpec.class);
        var system = mock(ChatClient.PromptSystemSpec.class);
        var call = mock(ChatClient.CallResponseSpec.class);
        var loader = mock(ResourceLoader.class);
        var planner = mock(Resource.class);
        given(builder.build()).willReturn(client);
        given(loader.getResource("classpath:prompts/planner-system.st")).willReturn(planner);
        given(client.prompt()).willReturn(request);
        given(request.system(any(Consumer.class))).willAnswer(invocation -> {
            Consumer<ChatClient.PromptSystemSpec> configure = invocation.getArgument(0);
            configure.accept(system);
            return request;
        });
        given(request.user(anyString())).willReturn(request);
        given(request.call()).willReturn(call);
        given(call.entity(PlannerProposal.class))
                .willReturn(new PlannerProposal(
                        List.of(new PlannerProposal.Item("P1", "JAVA", "Java 基础", "考察基础能力")), "P1"));

        var service = new InterviewAiService(builder, loader, "test-model");
        verify(loader).getResource("classpath:prompts/planner-system.st");
        var context = new PlannerContext(
                1L,
                "COMPREHENSIVE",
                null,
                1,
                "候选人",
                "简历",
                java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("项目", "项目")),
                "岗位",
                "要求",
                List.of("COMPREHENSIVE"),
                List.of("JAVA"));
        for (int i = 0; i < 2; i++) {
            assertThat(service.preparePlan(context).initialItemIndex()).isZero();
        }
        verify(loader, times(1)).getResource("classpath:prompts/planner-system.st");
        verify(system, times(2)).text(same(planner));
    }
}

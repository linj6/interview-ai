package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.api.ProjectContext;
import com.linj6.interviewagent.ai.api.decision.*;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.focus.FocusSelectionContext;
import com.linj6.interviewagent.ai.api.planner.PlannerContext;
import com.linj6.interviewagent.ai.api.question.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

class ProjectsPromptTest {
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "planner",
                "main-question-generation",
                "follow-up-question-generation",
                "evaluation",
                "decision",
                "focus-selection"
            })
    void systemPromptPreservesCrossProjectAttributionBoundary(String capability) throws Exception {
        String prompt = new DefaultResourceLoader()
                .getResource("classpath:prompts/" + capability + "-system.st")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(prompt).contains("不同项目的行为、场景、技术措施与结果不得无依据混合归因", "项目列表为空时不得推断候选人做过项目");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7})
    void everyCapabilityFormatsAllProjectsOnlyAtPromptBoundary(int count) {
        var projects = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new ProjectContext("项目" + i, "描述" + i))
                .toList();
        var builder = mock(ChatClient.Builder.class);
        var client = mock(ChatClient.class);
        var request = mock(ChatClient.ChatClientRequestSpec.class);
        var user = mock(ChatClient.PromptUserSpec.class);
        var rendered = new ArrayList<String>();
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(request);
        when(request.system(any(Consumer.class))).thenReturn(request);
        when(request.user(anyString())).thenAnswer(invocation -> {
            rendered.add(invocation.getArgument(0));
            return request;
        });
        when(request.user(any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<ChatClient.PromptUserSpec> configure = invocation.getArgument(0);
            configure.accept(user);
            return request;
        });
        when(user.text(anyString())).thenReturn(user);
        when(user.param(anyString(), any())).thenAnswer(invocation -> {
            if ("projects".equals(invocation.getArgument(0))) rendered.add(invocation.getArgument(1));
            return user;
        });
        var stop = new IllegalStateException("停止在模型调用前");
        when(request.call()).thenThrow(stop);
        var service = new InterviewAiService(builder, new DefaultResourceLoader(), "test");
        var actions = List.<Runnable>of(
                () -> service.preparePlan(new PlannerContext(
                        1L,
                        "COMPREHENSIVE",
                        null,
                        5,
                        "name",
                        "resume",
                        projects,
                        "title",
                        "description",
                        List.of("COMPREHENSIVE"),
                        List.of("JAVA"))),
                () -> service.generateMainQuestion(MainQuestionGenerationContext.builder()
                        .focus("方向")
                        .projects(projects)
                        .build()),
                () -> service.generateFollowUpQuestion(FollowUpQuestionGenerationContext.builder()
                        .followUpTarget("职责")
                        .projects(projects)
                        .build()),
                () -> service.selectFocus(
                        FocusSelectionContext.builder().projects(projects).build()),
                () -> service.evaluateAnswer(EvaluationContext.builder()
                        .projects(projects)
                        .answerContent("回答")
                        .dimensions(List.of())
                        .build()),
                () -> service.decideNext(DecisionContext.builder()
                        .projects(projects)
                        .evaluationMissingPoints(List.of())
                        .unusedPlanItems(List.of())
                        .progress(new InterviewProgress(1, 0, 5, 2))
                        .allowedActions(Set.of(DecisionAction.END))
                        .build()));
        for (Runnable action : actions) {
            rendered.clear();
            assertThatThrownBy(action::run).isSameAs(stop);
            assertThat(rendered).hasSize(1);
            String prompt = rendered.get(0);
            assertThat(prompt).contains("不同项目经历不得无依据混合归因");
            if (count == 0) assertThat(prompt).contains("未提供项目经历").doesNotContain("--- 项目 1 ---");
            for (int i = 1; i <= count; i++)
                assertThat(prompt)
                        .contains("--- 项目 " + i + " ---\n名称：项目" + i + "\n描述：描述" + i + "\n--- 项目 " + i + " 结束 ---");
        }
    }
}

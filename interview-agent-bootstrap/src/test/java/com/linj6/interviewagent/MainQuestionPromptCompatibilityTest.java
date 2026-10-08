package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

class MainQuestionPromptCompatibilityTest {
    @Test
    void legacyConstructorExpressesFocusOnlyAndUnavailableHistory() {
        var context = new MainQuestionGenerationContext(
                "事务隔离",
                "候选人",
                "简历",
                java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("项目", "项目")),
                "岗位",
                "描述");
        assertLegacyPrompt(context);
    }

    @Test
    void legacyBuilderExpressesFocusOnlyAndUnavailableHistory() {
        var context = MainQuestionGenerationContext.builder()
                .focus("事务隔离")
                .candidateName("候选人")
                .resumeSummary("简历")
                .projects(java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("项目", "项目")))
                .positionTitle("岗位")
                .positionDescription("描述")
                .build();
        assertLegacyPrompt(context);
    }

    @Test
    void openingExpressesCompleteItemAndExplicitEmptyHistory() {
        var context = MainQuestionGenerationContext.builder()
                .focus("事务隔离")
                .area("MYSQL")
                .reason("初始项理由")
                .previousMainQuestions(List.of())
                .candidateName("候选人")
                .build();
        assertThat(context.hasCompletePlanItem()).isTrue();
        assertThat(context.previousMainQuestions()).isEmpty();
        assertThat(userPrompt(context))
                .contains(
                        "完整 PlanItem 已提供",
                        "当前 PlanItem area：MYSQL",
                        "当前 PlanItem focus：事务隔离",
                        "当前 PlanItem reason：初始项理由",
                        "previousMainQuestions：[]（历史 MAIN 已提供）")
                .doesNotContain("仅 focus 可用", "历史 MAIN 不可用", "area：null", "reason：null");
    }

    @Test
    void completeItemDoesNotImplyHistoryWasProvided() {
        var context = MainQuestionGenerationContext.builder()
                .focus("事务隔离")
                .area("MYSQL")
                .reason("理由")
                .build();
        assertThat(userPrompt(context))
                .contains("完整 PlanItem 已提供", "previousMainQuestions：未提供")
                .doesNotContain("previousMainQuestions：[]");
    }

    @Test
    void providedHistoryIsCopiedAndIncludedInPrompt() {
        var history = new ArrayList<>(List.of("之前生成的 MAIN"));
        var context = MainQuestionGenerationContext.builder()
                .focus("事务隔离")
                .area("MYSQL")
                .reason("理由")
                .previousMainQuestions(history)
                .build();
        history.clear();
        assertThat(context.previousMainQuestions()).containsExactly("之前生成的 MAIN");
        assertThat(userPrompt(context)).contains("previousMainQuestions：[之前生成的 MAIN]（历史 MAIN 已提供）");
    }

    private void assertLegacyPrompt(MainQuestionGenerationContext context) {
        assertThat(context.hasCompletePlanItem()).isFalse();
        assertThat(context.previousMainQuestions()).isNull();
        assertThat(userPrompt(context))
                .contains(
                        "仅 focus 可用",
                        "完整 PlanItem 未提供",
                        "area、reason 不可用",
                        "考察 focus：事务隔离",
                        "previousMainQuestions：未提供",
                        "不能据此认定没有历史或当前为首题")
                .doesNotContain(
                        "当前 PlanItem area",
                        "当前 PlanItem reason",
                        "当前 PlanItem focus",
                        "previousMainQuestions：[]",
                        "null");
    }

    private String userPrompt(MainQuestionGenerationContext context) {
        var builder = mock(ChatClient.Builder.class);
        var client = mock(ChatClient.class);
        var request = mock(ChatClient.ChatClientRequestSpec.class);
        var call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(request);
        when(request.system(any(Consumer.class))).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.entity(GeneratedQuestion.class)).thenReturn(new GeneratedQuestion("生成的问题"));
        new InterviewAiService(builder, new DefaultResourceLoader(), "test-model").generateMainQuestion(context);
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(request).user(prompt.capture());
        return prompt.getValue();
    }
}

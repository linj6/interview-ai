package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.api.question.FollowUpQuestionGenerationContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

class FollowUpQuestionPromptContractTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            明确支持归因 | 我通过 Dominator Tree 找到了缓存持有的大对象。 | 缓存对象持有关系 | 明确确认：只有 Answer 明确确认的事实
            否定实现 | 我没做过压测。 | 压测后的瓶颈定位过程 | 明确否定：Answer 明确说
            记不清细节 | 我参与过压测，但记不清具体 QPS。 | 压测指标与瓶颈定位 | 也不能要求再次回忆已明确记不清的同一细节
            更强目标 | 我用过 MAT。 | 通过 MAT 的 Dominator Tree 发现泄漏对象及引用链 | target 比 Answer 更强的表述
            MAT 回归 | 用过 MAT，但不记得 Dominator Tree 的具体信息。 | 通过 MAT 的 Dominator Tree 发现泄漏对象及引用链 | MAT / Dominator Tree 示例
            无法确认 | 我无法确认是否使用过 Dominator Tree。 | Dominator Tree 分析结果 | 不知道 / 没做 / 记不清 / 无法确认
            假设建议 | 如果重新排查，我会考虑 MAT。 | MAT 排查步骤 | 追问必须保留“如果 / 假设”的语气
            """)
    void sendsAttributionSafetyContractWithOriginalEvidence(
            String scenario, String answer, String target, String requiredRule) throws Exception {
        var context = FollowUpQuestionGenerationContext.builder()
                .followUpTarget(target)
                .currentQuestionContent("你通过 Dominator Tree 发现了哪些泄漏对象？")
                .currentAnswerContent(answer)
                .build();
        assertThat(userPrompt(context, requiredRule))
                .contains(
                        "followUpTarget（唯一追问方向，不是候选人事实）: " + target,
                        "currentAnswer（候选人本轮陈述的事实来源）: " + answer,
                        "currentQuestion（考察上下文，不是 Candidate Fact Source）: " + context.currentQuestionContent())
                .doesNotContain("Strengths:", "Weaknesses:", "Missing points:", "Evaluation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"项目使用消息队列。", "没做过压测。", "如果重新设计，我会考虑压测。", "我做过压测。"})
    void sendsOriginalAnswerWithExplicitInputRolesAndNoEvaluation(String answer) throws Exception {
        var context = FollowUpQuestionGenerationContext.builder()
                .followUpTarget("压测中的瓶颈定位")
                .currentQuestionContent("你做过压测后如何定位瓶颈？")
                .currentAnswerContent(answer)
                .candidateName("候选人")
                .resumeSummary("简历背景")
                .projects(java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("项目", "项目背景")))
                .positionTitle("后端岗位")
                .positionDescription("岗位要求")
                .build();
        assertThat(userPrompt(context))
                .contains(
                        "followUpTarget（唯一追问方向，不是候选人事实）: 压测中的瓶颈定位",
                        "currentQuestion（考察上下文，不是 Candidate Fact Source）: 你做过压测后如何定位瓶颈？",
                        "currentAnswer（候选人本轮陈述的事实来源）: " + answer,
                        "Candidate: 候选人",
                        "Resume: 简历背景",
                        "名称：项目",
                        "描述：项目背景",
                        "Position: 后端岗位",
                        "Position description: 岗位要求")
                .doesNotContain("Strengths:", "Weaknesses:", "Missing points:", "Evaluation");
    }

    private String userPrompt(FollowUpQuestionGenerationContext context) throws Exception {
        return userPrompt(context, "currentAnswer（当前 Answer）是候选人本轮陈述的事实来源");
    }

    private String userPrompt(FollowUpQuestionGenerationContext context, String requiredRule) throws Exception {
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
        new InterviewAiService(builder, new DefaultResourceLoader(), "test-model").generateFollowUpQuestion(context);
        var system = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(request).system(system.capture());
        var systemSpec = mock(ChatClient.PromptSystemSpec.class);
        system.getValue().accept(systemSpec);
        var systemText = org.mockito.ArgumentCaptor.forClass(org.springframework.core.io.Resource.class);
        verify(systemSpec).text(systemText.capture());
        assertThat(systemText.getValue().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains(
                        requiredRule,
                        "只决定考察方向，不提供事实权限",
                        "你刚才提到 / 根据你刚才说明",
                        "未确认、推断、假设或建议必须使用开放或假设语气",
                        "只能围绕同一 target 内仍独立可回答的范围开放追问",
                        "不能通过省略归因词暗中预设为已确认事实",
                        "不得用“如果”包装对同一历史细节的重复回忆要求");
        verify(request, times(1)).call();
        verify(call, times(1)).entity(GeneratedQuestion.class);
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(request).user(prompt.capture());
        return prompt.getValue();
    }
}

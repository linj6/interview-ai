package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.api.evaluation.AnswerForm;
import com.linj6.interviewagent.ai.api.evaluation.Applicability;
import com.linj6.interviewagent.ai.api.evaluation.DimensionEvaluationProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationContext;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationDimensionContext;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceType;
import com.linj6.interviewagent.ai.api.evaluation.ReferenceAnswerProposal;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ResourceLoader;

class EvaluatorRetryTest {

    @Test
    @SuppressWarnings("unchecked")
    void callsModelOnceForValidEvaluation() {
        Fixture fixture = fixture();
        given(fixture.call().responseEntity(EvaluationProposal.class))
                .willReturn(response("provider-model", proposal(new BigDecimal("8.0"))));

        var result = fixture.service().evaluateAnswer(context());

        assertThat(result.overallScore10()).isEqualByComparingTo("8.00");
        assertThat(result.model()).isEqualTo("provider-model");
        verify(fixture.call(), times(1)).responseEntity(EvaluationProposal.class);
    }

    @Test
    void usesConfiguredModelWhenProviderModelIsNull() {
        assertModelFallback(null);
    }

    @Test
    void usesConfiguredModelWhenProviderModelIsEmpty() {
        assertModelFallback("");
    }

    @Test
    void usesConfiguredModelWhenProviderModelIsWhitespace() {
        assertModelFallback(" \t ");
    }

    private void assertModelFallback(String providerModel) {
        Fixture fixture = fixture();
        given(fixture.call().responseEntity(EvaluationProposal.class))
                .willReturn(response(providerModel, proposal(new BigDecimal("8.0"))));

        var result = fixture.service().evaluateAnswer(context());

        assertThat(result.model()).isEqualTo("test-model");
    }

    @Test
    void propagatesFirstFailureWithoutRetry() {
        Fixture fixture = fixture();
        given(fixture.call().responseEntity(EvaluationProposal.class))
                .willThrow(new IllegalStateException("temporary model failure"));

        assertThatThrownBy(() -> fixture.service().evaluateAnswer(context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary model failure");
        verify(fixture.call(), times(1)).responseEntity(EvaluationProposal.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Fixture fixture() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        given(builder.build()).willReturn(client);
        given(client.prompt()).willReturn(request);
        given(request.system(any(Consumer.class))).willReturn(request);
        given(request.user(any(Consumer.class))).willReturn(request);
        given(request.call()).willReturn(call);
        return new Fixture(new InterviewAiService(builder, resourceLoader, "test-model"), call);
    }

    private EvaluationContext context() {
        return EvaluationContext.builder()
                .interviewId(1L)
                .questionId(2L)
                .answerId(3L)
                .profileId(4L)
                .questionContent("问题")
                .answerContent("回答原文")
                .candidateName("候选人")
                .resumeSummary("简历")
                .projects(java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("项目", "项目")))
                .positionTitle("职位")
                .positionDescription("描述")
                .dimensions(List.of(
                        new EvaluationDimensionContext("ACCURACY", "准确性", "标准", "0-10", BigDecimal.ONE, false, 1)))
                .build();
    }

    private EvaluationProposal proposal(BigDecimal score) {
        return new EvaluationProposal(
                List.of(new DimensionEvaluationProposal(
                        "ACCURACY",
                        Applicability.APPLICABLE,
                        score,
                        "评分说明",
                        List.of(new EvidenceProposal(
                                EvidenceType.OVERALL_OBSERVATION, java.util.Optional.empty(), "观察")))),
                List.of(),
                List.of(),
                List.of(),
                new ReferenceAnswerProposal(AnswerForm.SAMPLE_ANSWER, List.of("要点"), "参考回答"));
    }

    private ResponseEntity<ChatResponse, EvaluationProposal> response(String model, EvaluationProposal proposal) {
        ChatResponseMetadata metadata =
                ChatResponseMetadata.builder().model(model).build();
        ChatResponse chatResponse =
                ChatResponse.builder().generations(List.of()).metadata(metadata).build();
        return new ResponseEntity<>(chatResponse, proposal);
    }

    private record Fixture(InterviewAiService service, ChatClient.CallResponseSpec call) {}
}

package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.api.focus.FocusProposal;
import com.linj6.interviewagent.ai.api.focus.FocusSelectionContext;
import com.linj6.interviewagent.ai.evaluator.EvaluationValidationException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

class FocusSelectorValidationTest {
    @Test
    void rejectsBlankFocusAsInvalidAiResponse() {
        assertThatThrownBy(() -> select(" \t\n "))
                .isInstanceOf(EvaluationValidationException.class)
                .hasMessage("Focus must not be blank");
    }

    @Test
    void rejectsFocusLongerThanQuestionColumnLimit() {
        assertThatThrownBy(() -> select("x".repeat(FocusProposal.MAX_LENGTH + 1)))
                .isInstanceOf(EvaluationValidationException.class)
                .hasMessage("Focus exceeds maximum length of 500");
    }

    @Test
    void trimsBeforeApplyingTheQuestionColumnLimit() {
        String focus = "x".repeat(FocusProposal.MAX_LENGTH);

        assertThat(select("  " + focus + "  ").focus()).isEqualTo(focus);
    }

    @Test
    void rejectsMissingFocusProposalAsInvalidAiResponse() {
        assertThatThrownBy(() -> select(null))
                .isInstanceOf(EvaluationValidationException.class)
                .hasMessage("FocusProposal must not be null");
    }

    private FocusProposal select(String value) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        given(builder.build()).willReturn(client);
        given(client.prompt()).willReturn(request);
        given(request.system(any(Consumer.class))).willReturn(request);
        given(request.user(any(String.class))).willReturn(request);
        given(request.call()).willReturn(call);
        given(call.entity(FocusProposal.class)).willReturn(value == null ? null : new FocusProposal(value));

        InterviewAiService service = new InterviewAiService(builder, new DefaultResourceLoader(), "test-model");
        return service.selectFocus(FocusSelectionContext.builder()
                .candidateName("candidate")
                .positionTitle("engineer")
                .build());
    }
}

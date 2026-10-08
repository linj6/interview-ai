package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;

import com.linj6.interviewagent.ai.api.planner.*;
import com.linj6.interviewagent.ai.planner.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlannerProposalValidatorTest {
    final PlannerProposalValidator validator = new PlannerProposalValidator();

    PlannerContext context(String mode, String area) {
        return new PlannerContext(
                1L,
                mode,
                area,
                2,
                "name",
                "resume",
                java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("project", "project")),
                "title",
                "description",
                java.util.Arrays.stream(com.linj6.interviewagent.interview.domain.InterviewMode.values())
                        .map(Enum::name)
                        .toList(),
                java.util.Arrays.stream(com.linj6.interviewagent.interview.domain.KnowledgeArea.values())
                        .map(Enum::name)
                        .toList());
    }

    PlannerProposal proposal(int count, String initial) {
        return new PlannerProposal(
                java.util.stream.IntStream.rangeClosed(1, count)
                        .mapToObj(i -> new PlannerProposal.Item("P" + i, "JAVA", "focus" + i, "reason"))
                        .toList(),
                initial);
    }

    @Test
    void acceptsBothCountBoundariesAndResolvesInitial() {
        assertThat(validator
                        .validate(context("COMPREHENSIVE", null), proposal(2, "P2"))
                        .initialItemIndex())
                .isEqualTo(1);
        assertThat(validator
                        .validate(context("TECHNICAL_FOCUS", "JAVA"), proposal(5, "P1"))
                        .items())
                .hasSize(5);
        assertThat(validator
                        .validate(context("PROJECT_DEEP_DIVE", null), proposal(2, "P1"))
                        .items())
                .hasSize(2);
    }

    @Test
    void rejectsInvalidCountsAndInitialReferences() {
        for (int n : List.of(0, 1, 6))
            assertThatThrownBy(() -> validator.validate(context("COMPREHENSIVE", null), proposal(n, "P1")))
                    .isInstanceOf(IllegalArgumentException.class);
        for (String ref : Arrays.asList(null, "P0", "P01", "P3", " P1"))
            assertThatThrownBy(() -> validator.validate(context("COMPREHENSIVE", null), proposal(2, ref)))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(context("COMPREHENSIVE", null), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidConfiguration() {
        for (var c : List.of(
                context("COMPREHENSIVE", "JAVA"),
                context("PROJECT_DEEP_DIVE", "JAVA"),
                context("TECHNICAL_FOCUS", null),
                context("TECHNICAL_FOCUS", "OTHER"),
                context("OTHER", null),
                context(null, null)))
            assertThatThrownBy(() -> validator.validate(c, proposal(2, "P1")))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedDuplicateReferencesAreasAndBlankText() {
        var valid = new PlannerProposal.Item("P1", "JAVA", "focus", "reason");
        for (var bad : Arrays.asList(
                null,
                valid,
                new PlannerProposal.Item("P0", "JAVA", "f", "r"),
                new PlannerProposal.Item("P01", "JAVA", "f", "r"),
                new PlannerProposal.Item("p2", "JAVA", "f", "r"),
                new PlannerProposal.Item("P2", "OTHER", "f", "r"),
                new PlannerProposal.Item("P2", "JAVA", " ", "r"),
                new PlannerProposal.Item("P2", "JAVA", "f", null))) {
            assertThatThrownBy(() -> validator.validate(
                            context("COMPREHENSIVE", null), new PlannerProposal(Arrays.asList(valid, bad), "P1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> validator.validate(context("TECHNICAL_FOCUS", "MYSQL"), proposal(2, "P1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void realCapabilityValidatesProposalAndUsesPlannerPrompt() throws Exception {
        var builder = org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.Builder.class);
        var client = org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.class);
        var request =
                org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec.class);
        var call = org.mockito.Mockito.mock(org.springframework.ai.chat.client.ChatClient.CallResponseSpec.class);
        org.mockito.Mockito.when(builder.build()).thenReturn(client);
        org.mockito.Mockito.when(client.prompt()).thenReturn(request);
        org.mockito.Mockito.when(request.system(org.mockito.ArgumentMatchers.any(java.util.function.Consumer.class)))
                .thenReturn(request);
        org.mockito.Mockito.when(request.user(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(request);
        org.mockito.Mockito.when(request.call()).thenReturn(call);
        org.mockito.Mockito.when(call.entity(PlannerProposal.class)).thenReturn(proposal(2, "P2"), proposal(2, "P3"));
        var service = new com.linj6.interviewagent.ai.InterviewAiService(
                builder, new org.springframework.core.io.DefaultResourceLoader(), "test-model");
        var callerContext = allowedContext("CALLER_MODE", null, List.of("CALLER_MODE"), List.of("JAVA", "CALLER_AREA"));
        assertThat(service.preparePlan(callerContext).initialItemIndex()).isEqualTo(1);
        assertThatThrownBy(() -> service.preparePlan(callerContext))
                .isInstanceOf(com.linj6.interviewagent.ai.evaluator.EvaluationValidationException.class);
        var prompts = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(request, org.mockito.Mockito.times(2)).user(prompts.capture());
        assertThat(prompts.getAllValues()).allSatisfy(prompt -> assertThat(prompt)
                .contains("allowedInterviewModes=[CALLER_MODE]", "allowedKnowledgeAreas=[JAVA, CALLER_AREA]"));
        var resource = new org.springframework.core.io.ClassPathResource("prompts/planner-system.st");
        assertThat(resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains(
                        "TECHNICAL_FOCUS",
                        "maxMainQuestions + 3",
                        "initialPlanItemRef",
                        "不是候选人经历",
                        "allowedInterviewModes",
                        "allowedKnowledgeAreas")
                .doesNotContain("JAVA", "MYSQL", "DISTRIBUTED_SYSTEMS");
    }

    PlannerContext allowedContext(String mode, String area, List<String> modes, List<String> areas) {
        return new PlannerContext(
                1L,
                mode,
                area,
                2,
                "name",
                "resume",
                java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("project", "project")),
                "title",
                "description",
                modes,
                areas);
    }

    @Test
    void usesCallerAllowListsInsteadOfAnInternalEnumCatalog() {
        var c = allowedContext("CALLER_MODE", null, List.of("CALLER_MODE"), List.of("CALLER_AREA"));
        var p = new PlannerProposal(
                List.of(
                        new PlannerProposal.Item("P1", "CALLER_AREA", "f", "r"),
                        new PlannerProposal.Item("P2", "CALLER_AREA", "g", "r")),
                "P1");
        assertThat(validator.validate(c, p).items())
                .extracting(ValidatedPlanResult.Item::area)
                .containsOnly("CALLER_AREA");
        assertThatThrownBy(() -> validator.validate(c, proposal(2, "P1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(
                        allowedContext("COMPREHENSIVE", null, List.of("CALLER_MODE"), List.of("JAVA")),
                        proposal(2, "P1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(
                        allowedContext("CALLER_MODE", null, List.of(), List.of("JAVA")), proposal(2, "P1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(
                        allowedContext("COMPREHENSIVE", null, List.of("COMPREHENSIVE"), List.of()), proposal(2, "P1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void technicalFocusStillRequiresAllowedAreaAndMatchingItems() {
        var modes = List.of("TECHNICAL_FOCUS");
        var areas = List.of("JAVA", "MYSQL");
        assertThat(validator
                        .validate(allowedContext("TECHNICAL_FOCUS", "JAVA", modes, areas), proposal(2, "P1"))
                        .items())
                .hasSize(2);
        for (String focus : Arrays.asList(null, "UNKNOWN", "MYSQL"))
            assertThatThrownBy(() -> validator.validate(
                            allowedContext("TECHNICAL_FOCUS", focus, modes, areas), proposal(2, "P1")))
                    .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextSnapshotsCallerAllowLists() {
        var modes = new ArrayList<>(List.of("COMPREHENSIVE"));
        var areas = new ArrayList<>(List.of("JAVA"));
        var c = allowedContext("COMPREHENSIVE", null, modes, areas);
        modes.clear();
        areas.clear();
        assertThat(validator.validate(c, proposal(2, "P1")).items()).hasSize(2);
        assertThatThrownBy(() -> c.allowedKnowledgeAreas().add("OTHER"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void applicationBuildsAllowListsFromDomainEnums() {
        var interviews = org.mockito.Mockito.mock(
                com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewRepository.class);
        var plans = org.mockito.Mockito.mock(
                com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewPlanRepository.class);
        var snapshot = org.mockito.Mockito.mock(
                com.linj6.interviewagent.interview.infrastructure.persistence.repository.InterviewSnapshot.class);
        org.mockito.Mockito.when(interviews.findById(1L)).thenReturn(snapshot);
        org.mockito.Mockito.when(snapshot.id()).thenReturn(1L);
        org.mockito.Mockito.when(snapshot.status())
                .thenReturn(com.linj6.interviewagent.interview.domain.InterviewStatus.CREATED);
        org.mockito.Mockito.when(snapshot.mode())
                .thenReturn(com.linj6.interviewagent.interview.domain.InterviewMode.COMPREHENSIVE);
        var candidates = org.mockito.Mockito.mock(com.linj6.interviewagent.candidate.api.CandidateQueryApi.class);
        var positions = org.mockito.Mockito.mock(com.linj6.interviewagent.position.api.PositionQueryApi.class);
        org.mockito.Mockito.when(candidates.getInterviewProfile(org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.mockito.Mockito.mock(
                        com.linj6.interviewagent.candidate.api.CandidateInterviewProfile.class));
        org.mockito.Mockito.when(positions.getInterviewProfile(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        org.mockito.Mockito.mock(com.linj6.interviewagent.position.api.PositionInterviewProfile.class));
        var ai = org.mockito.Mockito.mock(com.linj6.interviewagent.ai.api.InterviewAiApi.class);
        org.mockito.Mockito.when(ai.preparePlan(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("stop before persistence"));
        var service = new com.linj6.interviewagent.interview.application.InterviewPrepareApplicationService(
                interviews,
                plans,
                candidates,
                positions,
                ai,
                new com.linj6.interviewagent.interview.application.InterviewDecisionLimits(5, 2),
                org.mockito.Mockito.mock(org.springframework.transaction.support.TransactionTemplate.class));
        assertThatThrownBy(() -> service.prepareInterview(1L))
                .isInstanceOf(com.linj6.interviewagent.common.exception.BusinessException.class);
        var captured = org.mockito.ArgumentCaptor.forClass(PlannerContext.class);
        org.mockito.Mockito.verify(ai).preparePlan(captured.capture());
        assertThat(captured.getValue().allowedInterviewModes())
                .containsExactly(
                        java.util.Arrays.stream(com.linj6.interviewagent.interview.domain.InterviewMode.values())
                                .map(Enum::name)
                                .toArray(String[]::new));
        assertThat(captured.getValue().allowedKnowledgeAreas())
                .containsExactly(
                        java.util.Arrays.stream(com.linj6.interviewagent.interview.domain.KnowledgeArea.values())
                                .map(Enum::name)
                                .toArray(String[]::new));
    }
}

package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.InterviewPlan;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import com.linj6.interviewagent.interview.domain.PlanItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionQualityDomainTest {
    @Test
    void validatesEveryModeAndAreaCombination() {
        assertThat(KnowledgeArea.values()).hasSize(9);
        assertThat(InterviewMode.values()).hasSize(3);
        for (InterviewMode mode : InterviewMode.values()) {
            if (mode == InterviewMode.TECHNICAL_FOCUS) {
                assertThatThrownBy(() -> mode.validateFocusArea(null)).isInstanceOf(IllegalArgumentException.class);
                for (KnowledgeArea area : KnowledgeArea.values()) mode.validateFocusArea(area);
            } else {
                mode.validateFocusArea(null);
                for (KnowledgeArea area : KnowledgeArea.values())
                    assertThatThrownBy(() -> mode.validateFocusArea(area)).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Test
    void validatesPlanIdentityOwnershipInitialItemAndCount() {
        var item = new PlanItem(2L, 1L, KnowledgeArea.JAVA, "focus", "reason");
        var plan = new InterviewPlan(1L, 5L, 2L, List.of(item));
        plan.validateItemCount(1);
        assertThatThrownBy(() -> plan.validateItemCount(2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.validateItemCount(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewPlan(1L, 5L, 3L, List.of(item)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewPlan(3L, 5L, 2L, List.of(item)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InterviewPlan(1L, 5L, 2L, List.of(item, item)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanItem(2L, 1L, KnowledgeArea.JAVA, " ", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanItem(2L, 1L, KnowledgeArea.JAVA, "focus", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        var items = java.util.stream.LongStream.rangeClosed(2, 6)
                .mapToObj(id -> new PlanItem(id, 1L, KnowledgeArea.JAVA, "focus", "reason"))
                .toList();
        assertThatThrownBy(() -> new InterviewPlan(1L, 5L, 2L, items).validateItemCount(1))
                .isInstanceOf(IllegalArgumentException.class);
        new InterviewPlan(1L, 5L, 2L, items.subList(0, 4)).validateItemCount(1);
    }
}

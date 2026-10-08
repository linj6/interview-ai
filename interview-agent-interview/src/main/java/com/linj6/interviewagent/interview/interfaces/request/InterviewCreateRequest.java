package com.linj6.interviewagent.interview.interfaces.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.linj6.interviewagent.interview.domain.InterviewMode;
import com.linj6.interviewagent.interview.domain.KnowledgeArea;
import jakarta.validation.constraints.NotNull;

/** V1 创建配置；模式与方向组合由应用层校验。 */
public record InterviewCreateRequest(@NotNull InterviewMode mode, KnowledgeArea focusArea) {
    /** HTTP 仅接受枚举名称，避免 Jackson 将数字当作枚举序号。 */
    @JsonCreator
    public static InterviewCreateRequest fromJson(
            @JsonProperty("mode") String mode, @JsonProperty("focusArea") String focusArea) {
        return new InterviewCreateRequest(
                mode == null ? null : InterviewMode.valueOf(mode),
                focusArea == null ? null : KnowledgeArea.valueOf(focusArea));
    }
}

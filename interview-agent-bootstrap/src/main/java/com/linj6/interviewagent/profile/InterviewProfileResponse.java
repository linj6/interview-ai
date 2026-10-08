package com.linj6.interviewagent.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

/** 持久化后的完整资料响应。 */
public record InterviewProfileResponse(
        String candidateName,
        String resumeSummary,
        List<ProjectResponse> projects,
        String positionTitle,
        String positionDescription) {
    public record ProjectResponse(
            @JsonFormat(shape = JsonFormat.Shape.STRING) Long id, String name, String description) {}
}

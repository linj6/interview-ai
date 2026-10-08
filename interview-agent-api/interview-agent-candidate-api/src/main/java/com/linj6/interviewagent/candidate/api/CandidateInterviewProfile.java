package com.linj6.interviewagent.candidate.api;

import java.util.List;

/** 候选人及当前简历的结构化面试资料。 */
public record CandidateInterviewProfile(String candidateName, String resumeSummary, List<ProjectProfile> projects) {
    public CandidateInterviewProfile {
        projects = List.copyOf(projects);
    }
}

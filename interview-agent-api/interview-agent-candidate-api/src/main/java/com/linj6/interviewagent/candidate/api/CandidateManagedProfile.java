package com.linj6.interviewagent.candidate.api;

import java.util.List;

/** 独立于 AI Context 的资料管理模型。 */
public record CandidateManagedProfile(String candidateName, String resumeSummary, List<ManagedProject> projects) {}

package com.linj6.interviewagent.candidate.infrastructure.persistence.mapper;

import lombok.Getter;
import lombok.Setter;

/** Candidate 与 Resume 查询投影。 */
@Getter
@Setter
public class CandidateProfileProjection {
    /** 候选人姓名。 */
    private String name;
    /** 简历摘要。 */
    private String resumeSummary;
    /** 当前简历 ID，用于读取其全部项目。 */
    private Long resumeId;
}

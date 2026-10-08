package com.linj6.interviewagent.candidate.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linj6.interviewagent.candidate.api.CandidateInterviewProfile;
import com.linj6.interviewagent.candidate.api.ProjectProfile;
import com.linj6.interviewagent.candidate.infrastructure.persistence.entity.ProjectDO;
import com.linj6.interviewagent.candidate.infrastructure.persistence.mapper.CandidateMapper;
import com.linj6.interviewagent.candidate.infrastructure.persistence.mapper.CandidateProfileProjection;
import com.linj6.interviewagent.candidate.infrastructure.persistence.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Candidate 面试上下文持久化仓储。 */
@Repository
@RequiredArgsConstructor
public class CandidateRepository {
    private final CandidateMapper candidateMapper;
    private final ProjectMapper projectMapper;

    public CandidateInterviewProfile getInterviewProfile(Long candidateId) {
        CandidateProfileProjection profile = candidateMapper.selectInterviewProfile(candidateId);
        if (profile == null) {
            throw new IllegalStateException("Candidate 面试上下文不存在: " + candidateId);
        }
        return new CandidateInterviewProfile(
                profile.getName(),
                profile.getResumeSummary(),
                projectMapper
                        .selectList(new LambdaQueryWrapper<ProjectDO>()
                                .eq(ProjectDO::getResumeId, profile.getResumeId())
                                .orderByAsc(ProjectDO::getId))
                        .stream()
                        .map(project -> new ProjectProfile(project.getName(), project.getDescription()))
                        .toList());
    }
}

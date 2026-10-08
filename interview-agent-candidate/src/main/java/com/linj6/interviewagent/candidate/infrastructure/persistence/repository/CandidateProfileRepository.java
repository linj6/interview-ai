package com.linj6.interviewagent.candidate.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.linj6.interviewagent.candidate.api.*;
import com.linj6.interviewagent.candidate.infrastructure.persistence.entity.*;
import com.linj6.interviewagent.candidate.infrastructure.persistence.mapper.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Candidate、Current Resume 与项目的资料管理持久化。 */
@Repository
@RequiredArgsConstructor
public class CandidateProfileRepository {
    private final CandidateMapper candidates;
    private final ResumeMapper resumes;
    private final ProjectMapper projects;

    private ResumeDO currentResume(Long candidateId) {
        List<ResumeDO> current =
                resumes.selectList(new LambdaQueryWrapper<ResumeDO>().eq(ResumeDO::getCandidateId, candidateId));
        if (current.size() != 1) throw new CandidateProfileDataException("Current Resume 数据缺失或不唯一");
        return current.get(0);
    }

    public CandidateManagedProfile getProfile(Long candidateId) {
        CandidateDO candidate = candidates.selectById(candidateId);
        if (candidate == null) throw new CandidateProfileDataException("Current Candidate 不存在");
        ResumeDO resume = currentResume(candidateId);
        return new CandidateManagedProfile(
                candidate.getName(),
                resume.getSummary(),
                projects
                        .selectList(new LambdaQueryWrapper<ProjectDO>()
                                .eq(ProjectDO::getResumeId, resume.getId())
                                .orderByAsc(ProjectDO::getId))
                        .stream()
                        .map(p -> new ManagedProject(p.getId(), p.getName(), p.getDescription()))
                        .toList());
    }

    public void replaceProfile(Long candidateId, CandidateManagedProfile profile) {
        ResumeDO resume = currentResume(candidateId);
        int candidateRows = candidates.update(
                null,
                new LambdaUpdateWrapper<CandidateDO>()
                        .eq(CandidateDO::getId, candidateId)
                        .set(CandidateDO::getName, profile.candidateName()));
        requireOneRow(candidateRows);
        int resumeRows = resumes.update(
                null,
                new LambdaUpdateWrapper<ResumeDO>()
                        .eq(ResumeDO::getId, resume.getId())
                        .set(ResumeDO::getSummary, profile.resumeSummary()));
        requireOneRow(resumeRows);
        Set<Long> retained = new HashSet<>();
        for (ManagedProject p : profile.projects()) if (p.id() != null) retained.add(p.id());
        LambdaQueryWrapper<ProjectDO> removed =
                new LambdaQueryWrapper<ProjectDO>().eq(ProjectDO::getResumeId, resume.getId());
        if (!retained.isEmpty()) removed.notIn(ProjectDO::getId, retained);
        projects.delete(removed);
        for (ManagedProject p : profile.projects()) {
            if (p.id() == null) {
                ProjectDO row = new ProjectDO();
                row.setId(IdWorker.getId());
                row.setResumeId(resume.getId());
                row.setName(p.name());
                row.setDescription(p.description());
                projects.insert(row);
            } else {
                int projectRows = projects.update(
                        null,
                        new LambdaUpdateWrapper<ProjectDO>()
                                .eq(ProjectDO::getId, p.id())
                                .eq(ProjectDO::getResumeId, resume.getId())
                                .set(ProjectDO::getName, p.name())
                                .set(ProjectDO::getDescription, p.description()));
                requireOneRow(projectRows);
            }
        }
    }

    private void requireOneRow(int rows) {
        if (rows != 1) throw new CandidateProfileDataException("Current Profile 关键更新未命中唯一记录");
    }
}

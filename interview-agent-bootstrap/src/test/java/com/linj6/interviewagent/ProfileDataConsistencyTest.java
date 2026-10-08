package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.linj6.interviewagent.candidate.api.*;
import com.linj6.interviewagent.candidate.infrastructure.persistence.entity.*;
import com.linj6.interviewagent.candidate.infrastructure.persistence.mapper.*;
import com.linj6.interviewagent.candidate.infrastructure.persistence.repository.CandidateProfileRepository;
import com.linj6.interviewagent.position.api.*;
import com.linj6.interviewagent.position.infrastructure.persistence.mapper.PositionMapper;
import com.linj6.interviewagent.position.infrastructure.persistence.repository.PositionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfileDataConsistencyTest {
    private final CandidateMapper candidates = mock(CandidateMapper.class);
    private final ResumeMapper resumes = mock(ResumeMapper.class);
    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final CandidateProfileRepository repository = new CandidateProfileRepository(candidates, resumes, projects);
    private final CandidateManagedProfile profile =
            new CandidateManagedProfile("姓名", "简历", List.of(new ManagedProject(10L, "项目", "描述")));

    private ResumeDO resume() {
        ResumeDO row = new ResumeDO();
        row.setId(77L);
        return row;
    }

    @Test
    void rejectsMissingCandidate() {
        assertThatThrownBy(() -> repository.getProfile(1L)).isInstanceOf(CandidateProfileDataException.class);
        verifyNoInteractions(resumes, projects);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void rejectsMissingOrAmbiguousCurrentResume(int count) {
        when(candidates.selectById(1L)).thenReturn(new CandidateDO());
        when(resumes.selectList(any(Wrapper.class))).thenReturn(count == 0 ? List.of() : List.of(resume(), resume()));
        assertThatThrownBy(() -> repository.getProfile(1L)).isInstanceOf(CandidateProfileDataException.class);
        assertThatThrownBy(() -> repository.replaceProfile(1L, profile))
                .isInstanceOf(CandidateProfileDataException.class);
        verify(candidates, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(projects);
    }

    @ParameterizedTest
    @ValueSource(strings = {"candidate", "resume", "project"})
    void rejectsZeroRowsForCandidateResumeAndProject(String failed) {
        when(resumes.selectList(any(Wrapper.class))).thenReturn(List.of(resume()));
        when(candidates.update(isNull(), any(Wrapper.class))).thenReturn(failed.equals("candidate") ? 0 : 1);
        when(resumes.update(isNull(), any(Wrapper.class))).thenReturn(failed.equals("resume") ? 0 : 1);
        when(projects.update(isNull(), any(Wrapper.class))).thenReturn(failed.equals("project") ? 0 : 1);
        assertThatThrownBy(() -> repository.replaceProfile(1L, profile))
                .isInstanceOf(CandidateProfileDataException.class);
        if (failed.equals("candidate")) verify(resumes, never()).update(isNull(), any(Wrapper.class));
        if (!failed.equals("project")) verifyNoInteractions(projects);
    }

    @Test
    void rejectsMissingPositionAndZeroRowPositionUpdate() {
        PositionMapper mapper = mock(PositionMapper.class);
        PositionRepository positions = new PositionRepository(mapper);
        assertThatThrownBy(() -> positions.getInterviewProfile(1L)).isInstanceOf(PositionProfileDataException.class);
        assertThatThrownBy(() -> positions.replaceProfile(1L, new PositionInterviewProfile("岗位", "描述")))
                .isInstanceOf(PositionProfileDataException.class);
    }
}

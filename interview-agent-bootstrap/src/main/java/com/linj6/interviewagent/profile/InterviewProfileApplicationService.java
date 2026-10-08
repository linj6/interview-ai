package com.linj6.interviewagent.profile;

import static com.linj6.interviewagent.common.PersonalInstanceIdentity.*;

import com.linj6.interviewagent.candidate.api.*;
import com.linj6.interviewagent.common.api.CommonErrorCode;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import com.linj6.interviewagent.position.api.*;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单用户 Profile 聚合编排，仅通过模块 Public API 协作。 */
@Service
public class InterviewProfileApplicationService {
    private final CandidateProfileApi candidates;
    private final PositionQueryApi positions;
    private final PositionProfileApi positionWriter;
    private final Validator validator;

    public InterviewProfileApplicationService(
            CandidateProfileApi candidates,
            PositionQueryApi positions,
            PositionProfileApi positionWriter,
            Validator validator) {
        this.candidates = candidates;
        this.positions = positions;
        this.positionWriter = positionWriter;
        this.validator = validator;
    }

    public InterviewProfileResponse getProfile() {
        try {
            return response(candidates.getProfile(CANDIDATE_ID), positions.getInterviewProfile(POSITION_ID));
        } catch (CandidateProfileDataException | PositionProfileDataException exception) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_DATA_INCONSISTENT);
        }
    }

    /** 校验、四类事实写入及返回资料重读均处于同一本地事务。 */
    @Transactional
    public InterviewProfileResponse replaceProfile(InterviewProfileRequest request) {
        try {
            if (request == null || !validator.validate(request).isEmpty()) reject("Profile 参数不合法");
            checkText(request.resumeSummary());
            checkText(request.positionDescription());
            CandidateManagedProfile current = candidates.getProfile(CANDIDATE_ID);
            positions.getInterviewProfile(POSITION_ID);
            Set<Long> owned = new HashSet<>();
            current.projects().forEach(p -> owned.add(p.id()));
            Set<Long> seen = new HashSet<>();
            for (var p : request.projects()) {
                checkText(p.description());
                if (p.id() != null && (!seen.add(p.id()) || !owned.contains(p.id())))
                    reject("project id 重复或不属于 Current Resume");
            }
            candidates.replaceProfile(
                    CANDIDATE_ID,
                    new CandidateManagedProfile(
                            request.candidateName(),
                            request.resumeSummary(),
                            request.projects().stream()
                                    .map(p -> new ManagedProject(p.id(), p.name(), p.description()))
                                    .toList()));
            positionWriter.replaceProfile(
                    POSITION_ID, new PositionInterviewProfile(request.positionTitle(), request.positionDescription()));
            return getProfile();
        } catch (CandidateProfileDataException | PositionProfileDataException exception) {
            throw new BusinessException(InterviewErrorCode.INTERVIEW_DATA_INCONSISTENT);
        }
    }

    private void checkText(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > 65535) reject("TEXT 字段超过 schema 字节限制");
    }

    private void reject(String message) {
        throw new BusinessException(CommonErrorCode.PARAM_ERROR, message);
    }

    private InterviewProfileResponse response(CandidateManagedProfile candidate, PositionInterviewProfile position) {
        return new InterviewProfileResponse(
                candidate.candidateName(),
                candidate.resumeSummary(),
                candidate.projects().stream()
                        .map(p -> new InterviewProfileResponse.ProjectResponse(p.id(), p.name(), p.description()))
                        .toList(),
                position.title(),
                position.description());
    }
}

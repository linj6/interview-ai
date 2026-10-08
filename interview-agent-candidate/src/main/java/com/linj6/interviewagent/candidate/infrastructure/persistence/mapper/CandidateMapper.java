package com.linj6.interviewagent.candidate.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linj6.interviewagent.candidate.infrastructure.persistence.entity.CandidateDO;
import org.apache.ibatis.annotations.Mapper;

/** Candidate 的 MyBatis-Plus Mapper。 */
@Mapper
public interface CandidateMapper extends BaseMapper<CandidateDO> {
    CandidateProfileProjection selectInterviewProfile(Long candidateId);
}

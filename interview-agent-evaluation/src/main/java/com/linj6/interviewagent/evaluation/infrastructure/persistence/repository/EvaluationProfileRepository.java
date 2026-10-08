package com.linj6.interviewagent.evaluation.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationDimension;
import com.linj6.interviewagent.evaluation.api.profile.EvaluationProfile;
import com.linj6.interviewagent.evaluation.domain.EvaluationErrorCode;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.entity.EvaluationDimensionConfigDO;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.entity.EvaluationProfileDO;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper.EvaluationDimensionConfigMapper;
import com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper.EvaluationProfileMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Evaluation Profile 持久化仓储。 */
@Repository
@RequiredArgsConstructor
public class EvaluationProfileRepository {

    private final EvaluationProfileMapper profileMapper;
    private final EvaluationDimensionConfigMapper dimensionMapper;

    public EvaluationProfile findById(Long profileId) {
        EvaluationProfileDO profile = profileMapper.selectById(profileId);
        return requireProfileWithDimensions(profile);
    }

    public EvaluationProfile findByCode(String profileCode) {
        EvaluationProfileDO profile = profileMapper.selectOne(
                Wrappers.<EvaluationProfileDO>lambdaQuery().eq(EvaluationProfileDO::getCode, profileCode));
        return requireProfileWithDimensions(profile);
    }

    private EvaluationProfile requireProfileWithDimensions(EvaluationProfileDO profile) {
        if (profile == null) {
            throw new BusinessException(EvaluationErrorCode.EVALUATION_PROFILE_NOT_FOUND);
        }
        List<EvaluationDimension> dimensions = dimensionMapper
                .selectList(Wrappers.<EvaluationDimensionConfigDO>lambdaQuery()
                        .eq(EvaluationDimensionConfigDO::getProfileId, profile.getId())
                        .orderByAsc(EvaluationDimensionConfigDO::getSortOrder))
                .stream()
                .map(this::toDimension)
                .toList();
        if (dimensions.isEmpty()) {
            throw new BusinessException(EvaluationErrorCode.EVALUATION_PROFILE_NOT_FOUND);
        }
        return new EvaluationProfile(profile.getId(), profile.getCode(), dimensions);
    }

    private EvaluationDimension toDimension(EvaluationDimensionConfigDO value) {
        return new EvaluationDimension(
                value.getDimensionCode(),
                value.getDimensionName(),
                value.getEvaluationCriteria(),
                value.getScoreGuidance(),
                value.getWeight(),
                value.getNaAllowed(),
                value.getSortOrder());
    }
}

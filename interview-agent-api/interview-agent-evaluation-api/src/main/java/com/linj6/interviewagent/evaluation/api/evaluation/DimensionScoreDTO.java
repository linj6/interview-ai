package com.linj6.interviewagent.evaluation.api.evaluation;

import java.math.BigDecimal;

/** 维度名称、适用性、分数与说明的数据快照。 */
public record DimensionScoreDTO(String name, boolean applicable, BigDecimal score, String explanation) {}

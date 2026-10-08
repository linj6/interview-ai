package com.linj6.interviewagent.interview.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linj6.interviewagent.interview.domain.InterviewPlan;
import com.linj6.interviewagent.interview.domain.PlanItem;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.InterviewPlanDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.entity.PlanItemDO;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.InterviewPlanMapper;
import com.linj6.interviewagent.interview.infrastructure.persistence.mapper.PlanItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Plan 聚合持久化；唯一约束保障一场面试至多一个 Plan，失败整批回滚。 */
@Repository
@RequiredArgsConstructor
public class InterviewPlanRepository {
    private final InterviewPlanMapper planMapper;
    private final PlanItemMapper itemMapper;

    /** 接收已分配真实 ID 的聚合；Prepare 的生命周期与并发状态校验由后续调用方负责。 */
    @Transactional
    public InterviewPlan save(InterviewPlan plan, int maxMainQuestions) {
        plan.validateItemCount(maxMainQuestions);
        InterviewPlanDO value = new InterviewPlanDO();
        value.setId(plan.id());
        value.setInterviewId(plan.interviewId());
        value.setInitialPlanItemId(plan.initialPlanItemId());
        planMapper.insert(value);
        for (PlanItem item : plan.items()) {
            PlanItemDO row = new PlanItemDO();
            row.setId(item.id());
            row.setPlanId(item.planId());
            row.setArea(item.area());
            row.setFocus(item.focus());
            row.setReason(item.reason());
            itemMapper.insert(row);
        }
        return plan;
    }

    public InterviewPlan findByInterviewId(Long interviewId) {
        var plan = planMapper.selectOne(
                Wrappers.<InterviewPlanDO>lambdaQuery().eq(InterviewPlanDO::getInterviewId, interviewId));
        if (plan == null) {
            return null;
        }
        var items = itemMapper
                .selectList(Wrappers.<PlanItemDO>lambdaQuery()
                        .eq(PlanItemDO::getPlanId, plan.getId())
                        .orderByAsc(PlanItemDO::getId))
                .stream()
                .map(row -> new PlanItem(row.getId(), row.getPlanId(), row.getArea(), row.getFocus(), row.getReason()))
                .toList();
        return new InterviewPlan(plan.getId(), plan.getInterviewId(), plan.getInitialPlanItemId(), items);
    }
}

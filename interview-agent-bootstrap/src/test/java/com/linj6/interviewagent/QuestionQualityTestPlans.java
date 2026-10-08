package com.linj6.interviewagent;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** 为 Turn/Skip 测试建立真实 Plan；不依赖 Planner 或真实 LLM。 */
final class QuestionQualityTestPlans {
    static List<Long> prepare(JdbcTemplate jdbc, long interviewId, String focus) {
        long planId = IdWorker.getId();
        var ids = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> IdWorker.getId())
                .toList();
        jdbc.update(
                "INSERT INTO interview_plan (id, interview_id, initial_plan_item_id) VALUES (?, ?, ?)",
                planId,
                interviewId,
                ids.get(0));
        for (Long id : ids)
            jdbc.update(
                    "INSERT INTO interview_plan_item (id, plan_id, area, focus, reason) VALUES (?, ?, 'JAVA', ?, '计划理由')",
                    id,
                    planId,
                    focus);
        return ids;
    }

    static Long next(JdbcTemplate jdbc, long interviewId) {
        return jdbc.queryForObject(
                "SELECT MIN(id) FROM interview_plan_item WHERE plan_id = (SELECT id FROM interview_plan WHERE interview_id = ?) AND id NOT IN (SELECT plan_item_id FROM question WHERE interview_id = ? AND type = 'MAIN' AND plan_item_id IS NOT NULL)",
                Long.class,
                interviewId,
                interviewId);
    }
}

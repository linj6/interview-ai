package com.linj6.interviewagent.ai.prompt;

import com.linj6.interviewagent.ai.api.ProjectContext;
import java.util.List;

/** 在 Prompt 边界格式化项目，保留每项事实的归属。 */
public final class ProjectPromptFormatter {
    private ProjectPromptFormatter() {}

    public static String format(List<ProjectContext> projects) {
        StringBuilder text = new StringBuilder("不同项目经历不得无依据混合归因；不得将一个项目的行为、场景或结果归到另一个项目。\n");
        if (projects.isEmpty()) return text.append("未提供项目经历，不得推断候选人做过项目。\n").toString();
        for (int i = 0; i < projects.size(); i++) {
            ProjectContext project = projects.get(i);
            text.append("--- 项目 ")
                    .append(i + 1)
                    .append(" ---\n名称：")
                    .append(project.name())
                    .append("\n描述：")
                    .append(project.description())
                    .append("\n--- 项目 ")
                    .append(i + 1)
                    .append(" 结束 ---\n");
        }
        return text.toString();
    }
}

package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linj6.interviewagent.ai.api.evaluation.EvaluationProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceProposal;
import com.linj6.interviewagent.ai.api.evaluation.EvidenceType;
import com.linj6.interviewagent.ai.api.focus.FocusProposal;
import com.linj6.interviewagent.ai.api.focus.FocusSelectionContext;
import com.linj6.interviewagent.ai.api.question.FollowUpQuestionGenerationContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import com.linj6.interviewagent.ai.decision.DecisionProposal;
import java.util.Arrays;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

class StructuredOutputSchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void evaluationRuntimeSchemaAllowsNullEvidenceRef() throws Exception {
        JsonNode schema = mapper.readTree(new BeanOutputConverter<>(EvaluationProposal.class).getJsonSchema());
        JsonNode evidence = schema.path("properties")
                .path("dimensionEvaluations")
                .path("items")
                .path("properties")
                .path("evidence")
                .path("items");
        JsonNode refTypes = evidence.path("properties").path("evidenceRef").path("type");

        assertThat(StreamSupport.stream(refTypes.spliterator(), false)
                        .map(JsonNode::asText)
                        .toList())
                .containsExactlyInAnyOrder("string", "null");
        assertThat(StreamSupport.stream(evidence.path("required").spliterator(), false)
                        .map(JsonNode::asText)
                        .toList())
                .contains("evidenceRef");
        // Schema 只表达可空形状；按 EvidenceType 校验 ref 的责任仍在 Java Validator。
    }

    @Test
    void evaluationConverterReadsObservationNullAndAnswerQuoteReference() {
        BeanOutputConverter<EvidenceProposal> converter = new BeanOutputConverter<>(EvidenceProposal.class);

        EvidenceProposal observation =
                converter.convert("{\"type\":\"OVERALL_OBSERVATION\",\"evidenceRef\":null,\"explanation\":\"整体观察\"}");
        EvidenceProposal answerQuote =
                converter.convert("{\"type\":\"ANSWER_QUOTE\",\"evidenceRef\":\"E1\",\"explanation\":\"原文依据\"}");

        assertThat(observation.type()).isEqualTo(EvidenceType.OVERALL_OBSERVATION);
        assertThat(observation.evidenceRef()).isEmpty();
        assertThat(answerQuote.evidenceRef()).contains("E1");
    }

    @Test
    void focusSelectorSchemaAndContextAreMinimalAndTurnIndependent() throws Exception {
        JsonNode schema = schemaFor(FocusProposal.class);
        assertThat(schema.path("properties").fieldNames()).toIterable().containsExactly("focus");
        assertThat(Arrays.stream(FocusSelectionContext.class.getRecordComponents())
                        .map(c -> c.getName()))
                .containsExactly(
                        "candidateName",
                        "resumeSummary",
                        "projects",
                        "positionTitle",
                        "positionDescription",
                        "selectedMainFocuses");
        assertThat(readPrompt("focus-selection-system.st"))
                .contains("不得读取或推断当前 Question、Answer、Evaluation")
                .contains("已选 MAIN focus 列表");
    }

    @Test
    void decisionSchemaHasNoFreeIntentAndQuestionContextsAreDisjoint() throws Exception {
        JsonNode decision = schemaFor(DecisionProposal.class);
        assertThat(decision.path("properties").has("intent")).isFalse();
        assertThat(decision.path("properties").has("targetRef")).isTrue();
        assertThat(Arrays.stream(MainQuestionGenerationContext.class.getRecordComponents())
                        .map(c -> c.getName()))
                .contains("area", "focus", "reason", "previousMainQuestions")
                .doesNotContain("currentQuestionContent", "currentAnswerContent", "missingPoints", "followUpTarget");
        assertThat(Arrays.stream(FollowUpQuestionGenerationContext.class.getRecordComponents())
                        .map(c -> c.getName()))
                .containsExactly(
                        "followUpTarget",
                        "currentQuestionContent",
                        "currentAnswerContent",
                        "candidateName",
                        "resumeSummary",
                        "projects",
                        "positionTitle",
                        "positionDescription");
    }

    @Test
    void questionPromptsHaveDifferentInputBoundaries() throws Exception {
        assertThat(readPrompt("main-question-generation-system.st"))
                .contains(
                        "area / focus / reason",
                        "previousMainQuestions",
                        "不得偏离当前 PlanItem",
                        "仅 focus 可用",
                        "历史 MAIN 未提供",
                        "不能推断没有历史或当前为首题",
                        "显式空列表")
                .doesNotContain("当前问题", "当前回答", "Evaluation");
        assertThat(readPrompt("follow-up-question-generation-system.st"))
                .contains("followUpTarget", "当前 Question", "Answer")
                .doesNotContain("Evaluation", "strengths", "weaknesses", "missingPoints");
        assertThat(schemaFor(GeneratedQuestion.class).path("properties").fieldNames())
                .toIterable()
                .containsExactly("content");
    }

    @Test
    void questionSchemasDescribeFactAuthorityContracts() throws Exception {
        assertThat(schemaFor(FocusProposal.class)
                        .path("properties")
                        .path("focus")
                        .path("description")
                        .asText())
                .contains("只表示考察方向", "不是候选人经历摘要", "不构成候选人事实来源", "不得重新组合、补全或建立资料未明确支持的事实关系");
        assertThat(schemaFor(GeneratedQuestion.class)
                        .path("properties")
                        .path("content")
                        .path("description")
                        .asText())
                .contains(
                        "候选人经历前提及事实关系必须由允许的事实来源明确支持",
                        "MAIN 使用 Candidate/Profile",
                        "FOLLOW_UP 还可使用当前 Answer",
                        "不得跨场景组合问题、措施或结果",
                        "未提供的实现细节应开放询问",
                        "不得作为既定事实预设");
    }

    @Test
    void systemPromptsDeclareGroundingRules() throws Exception {
        for (String name : new String[] {
            "focus-selection-system.st", "main-question-generation-system.st", "follow-up-question-generation-system.st"
        }) {
            assertThat(readPrompt(name))
                    .as(name)
                    .contains(
                            "Candidate/Profile 是候选人经历事实来源",
                            "岗位资料不证明候选人经历",
                            "只决定考察方向，不提供事实权限",
                            "同项目、相邻文本或相关技术不代表属于同一场景",
                            "不得跨场景重新组合问题、措施和结果",
                            "未明确的实现细节",
                            "开放问题",
                            "不作为既定事实预设");
        }
        assertThat(readPrompt("focus-selection-system.st")).contains("不是候选人经历摘要", "已选 focus 也不是事实来源", "资料未明确支持的事实关系");
        assertThat(readPrompt("follow-up-question-generation-system.st"))
                .contains(
                        "当前 Answer）是候选人本轮陈述的事实来源",
                        "不是 Candidate Fact Source",
                        "不代表候选人已经确认该事实",
                        "当前问题存在未经支持的前提时，不继续强化该前提");
    }

    @Test
    void followUpPromptRestrictsAttributionToCurrentAnswer() throws Exception {
        assertThat(readPrompt("follow-up-question-generation-system.st"))
                .contains(
                        "归因必须由当前 Answer 明确支持",
                        "不能替代当前 Answer 确认本轮事实",
                        "不能覆盖 Answer 的明确否定",
                        "只能在当前 target 范围内转化为开放式澄清",
                        "当时是否存在……",
                        "如果存在，是如何处理的",
                        "不得改写为候选人已经实施或实际发生的事实",
                        "不得为了让问题更具体而自行补充资料未支持的业务行为、数据流、写操作、组件、配置、机制或实施结果");
    }

    @Test
    void followUpPromptDefinesFourAnswerAttributionCases() throws Exception {
        assertThat(readPrompt("follow-up-question-generation-system.st"))
                .contains(
                        "未提及：Answer 未确认的内容",
                        "明确否定：Answer 明确说",
                        "不得询问该事实的实际实施过程",
                        "重复确认已否定事实",
                        "假设方案：Answer 中",
                        "追问必须保留“如果 / 假设”的语气",
                        "不得写成当时已经采用的实现或结果",
                        "明确确认：只有 Answer 明确确认的事实",
                        "归因范围不得超出原文支持的行为、场景和结果",
                        "确认做过某事不等于确认其具体机制或细节");
    }

    @Test
    void followUpPromptKeepsOnlyTheSelectedTarget() throws Exception {
        assertThat(readPrompt("follow-up-question-generation-system.st"))
                .contains("followUpTarget 是本次 FOLLOW_UP 唯一允许考察的目标", "不得重新选择、补充或合并其他目标", "即使它们与当前 target 技术相关");
    }

    @Test
    void evaluationPromptSeparatesAccuracyCompletenessAndVerticalDepth() throws Exception {
        assertThat(readPrompt("evaluation-system.st"))
                .contains(
                        "未提及某知识点不能作为 Accuracy 扣分依据",
                        "只评价 Question 明确要求的信息是否得到覆盖",
                        "可以沿候选人已经回答的话题纵向评价",
                        "不得横向扩展新的知识点",
                        "缺少解释不自动成为 Accuracy 的事实错误或 Completeness 的题目遗漏",
                        "简历、项目背景和岗位资料只帮助理解上下文",
                        "Profile Rubric 必须在下述维度边界内应用",
                        "如果题目未要求且回答未主动展开 Canal / Redis / 缓存一致性",
                        "不能仅因未谈这些题外知识而扣 Accuracy、Completeness、Depth");
    }

    @Test
    void evaluationPromptGroundsFeedbackAndKeepsUnknownFactsOutOfAbilityJudgments() throws Exception {
        assertThat(readPrompt("evaluation-system.st"))
                .contains(
                        "weaknesses 只能表示当前 Answer 已实际暴露出的回答质量或技术问题",
                        "“未验证 / 未提及 / 记不清”不能自动推断为能力弱点",
                        "missingPoints 必须从当前 Question + Answer 得到依据",
                        "Question 明确要求但尚未获得的信息",
                        "Answer 主动展开后仍存在的关键未澄清点",
                        "description 和 explanation 应指出题目要求或回答展开的依据",
                        "不存在的经历或事件不要求编造处理过程",
                        "不得仅因 Reference Answer、岗位要求或通用最佳实践包含某知识点就生成 MissingPoint",
                        "在 MissingPoint 注明候选人记不清",
                        "不能据此认定不会压测或技术能力不足");
    }

    @Test
    void evaluationRuntimeContractDoesNotUseReferenceAnswerAsChecklist() throws Exception {
        JsonNode properties = mapper.readTree(new BeanOutputConverter<>(EvaluationProposal.class).getJsonSchema())
                .path("properties");
        assertThat(properties.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "dimensionEvaluations", "strengths", "weaknesses", "missingPoints", "referenceAnswer");
        assertThat(properties.path("weaknesses").path("description").asText())
                .contains("当前 Answer 已实际暴露", "不能自动推断为能力弱点");
        assertThat(properties.path("missingPoints").path("description").asText())
                .contains("当前 Question + Answer", "Question 明确要求", "Answer 主动展开", "不得仅依据参考回答");
        assertThat(properties.path("referenceAnswer").path("description").asText())
                .contains("不是评分 checklist", "不得通过与其做知识点 diff 得出");
        JsonNode dimension =
                properties.path("dimensionEvaluations").path("items").path("properties");
        assertThat(dimension.path("scoreExplanation").path("description").asText())
                .contains("ACCURACY", "未提及不能扣分", "COMPLETENESS", "题目明确要求", "DEPTH", "不横向扩展知识点");
        JsonNode missing = properties.path("missingPoints").path("items").path("properties");
        assertThat(missing.path("description").path("description").asText()).contains("当前 Question + Answer 依据");
        assertThat(missing.path("explanation").path("description").asText()).contains("信息缺失原因", "不能推断为能力不足");
        assertThat(properties
                        .path("referenceAnswer")
                        .path("properties")
                        .path("content")
                        .path("description")
                        .asText())
                .contains("具体实现、配置、指标或效果", "通用建议保持为建议或可讨论方向", "不得反向影响评价");
        assertThat(readPrompt("evaluation-system.st"))
                .contains(
                        "先基于 Question + Answer 形成评价，再组织参考回答",
                        "Score / Dimension / Weakness / MissingPoint 不得通过与 Reference Answer 做知识点 diff 得出",
                        "keyPoints 不定义新的扣分项",
                        "项目经历题不得补造候选人未提供的具体实现、配置、指标或效果",
                        "通用建议必须保持为建议或可讨论方向");
    }

    @Test
    void evaluationPromptAndRuntimeSchemaRejectAccuracyOmissionAndHistoricalFactPollution() throws Exception {
        String prompt = readPrompt("evaluation-system.st");
        assertThat(prompt)
                .contains(
                        "Accuracy 只能因已表达的技术事实、因果关系或判断存在错误或明显不合理而扣分",
                        "扣分说明必须指出具体错误或不合理之处",
                        "“未提及 / 未展开 / 未深入讨论某机制”本身不得作为 Accuracy 扣分理由",
                        "即使该机制与题目相关",
                        "“当时没做 / 没发生 / 没有专门方案”是有效的历史事实回答",
                        "不能自动转化为能力 Weakness",
                        "已经明确没做或没发生的事实不是仍未回答的历史实现",
                        "不得要求补出不存在的方案",
                        "description / explanation 必须保持历史事实边界",
                        "“如果现在重新设计 / 现在会考虑”的方案只代表当前思考",
                        "不得反推为历史项目应该存在的实现");
        JsonNode properties = mapper.readTree(new BeanOutputConverter<>(EvaluationProposal.class).getJsonSchema())
                .path("properties");
        assertThat(properties
                        .path("dimensionEvaluations")
                        .path("items")
                        .path("properties")
                        .path("scoreExplanation")
                        .path("description")
                        .asText())
                .contains("只能因已表达内容错误或明显不合理而扣分", "必须指出具体问题", "未提及、未展开或未深入讨论机制本身不能扣分");
        assertThat(properties.path("weaknesses").path("description").asText())
                .contains("当时没做、没发生或没有专门方案是有效的历史事实回答", "不能自动转化为能力 Weakness");
        JsonNode missing = properties.path("missingPoints").path("items").path("properties");
        assertThat(missing.path("description").path("description").asText())
                .contains("明确没做或没发生的事实不是仍未回答的历史实现", "不得要求补出不存在的方案");
        assertThat(missing.path("explanation").path("description").asText())
                .contains("description / explanation 保持历史事实边界", "方案只代表当前思考", "不得反推为历史项目应该存在的实现");
    }

    @Test
    void answerFrameworkPromptAndRuntimeSchemaSeparateFactsUnknownsAndSuggestions() throws Exception {
        assertThat(readPrompt("evaluation-system.st"))
                .contains(
                        "ANSWER_FRAMEWORK 的 content 和 keyPoints 必须明确区分",
                        "Candidate/Profile/Answer 支持的历史事实",
                        "候选人明确不知道、没做、没发生或记不清的信息",
                        "当前可讨论或改进的技术建议",
                        "后两类不得写成候选人的历史项目实现",
                        "未获上述来源支持的实现、配置、指标、机制或效果，不得补入历史项目回答",
                        "必须明确标记为“如果现在重新设计 / 可进一步考虑”等建议语义");
        JsonNode properties = mapper.readTree(new BeanOutputConverter<>(EvaluationProposal.class).getJsonSchema())
                .path("properties")
                .path("referenceAnswer")
                .path("properties");
        assertThat(properties.path("content").path("description").asText())
                .contains(
                        "ANSWER_FRAMEWORK 的 content 和 keyPoints 必须明确区分 Candidate/Profile/Answer 支持的历史事实",
                        "明确不知道/没做/没发生/记不清的信息",
                        "当前可讨论或改进的建议",
                        "后两类不得写成历史项目实现",
                        "实现、配置、指标、机制或效果不得补入历史项目回答",
                        "建议明确标记为如果现在重新设计或可进一步考虑");
    }

    private JsonNode schemaFor(Class<?> type) throws Exception {
        return mapper.readTree(JsonSchemaGenerator.generateForType(type));
    }

    private String readPrompt(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/prompts/" + name)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}

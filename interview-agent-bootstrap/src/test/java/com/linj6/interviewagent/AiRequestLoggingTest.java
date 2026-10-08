package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.linj6.interviewagent.ai.InterviewAiService;
import com.linj6.interviewagent.ai.api.focus.FocusProposal;
import com.linj6.interviewagent.ai.api.focus.FocusSelectionContext;
import com.linj6.interviewagent.ai.api.question.GeneratedQuestion;
import com.linj6.interviewagent.ai.api.question.MainQuestionGenerationContext;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

class AiRequestLoggingTest {
    @Test
    void focusSelectorReceivesProfilesAndSelectedFocusesWithoutTurnContext() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        given(builder.build()).willReturn(client);
        given(client.prompt()).willReturn(request);
        given(request.system(any(Consumer.class))).willReturn(request);
        given(request.user(any(String.class))).willReturn(request);
        given(request.call()).willReturn(call);
        given(call.entity(FocusProposal.class)).willReturn(new FocusProposal("事务隔离下的并发更新"));

        Logger logger = (Logger) LoggerFactory.getLogger(InterviewAiService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        boolean previousAdditivity = logger.isAdditive();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        try {
            InterviewAiService service = new InterviewAiService(builder, new DefaultResourceLoader(), "test-model");
            FocusProposal focus = service.selectFocus(FocusSelectionContext.builder()
                    .candidateName("候选人")
                    .resumeSummary("简历摘要")
                    .projects(java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("项目", "项目摘要")))
                    .positionTitle("Java 工程师")
                    .positionDescription("岗位描述")
                    .selectedMainFocuses(java.util.List.of("MySQL 索引选择"))
                    .build());
            assertThat(focus.focus()).isEqualTo("事务隔离下的并发更新");
            ILoggingEvent event = appender.list.stream()
                    .filter(value -> value.getFormattedMessage().contains("capability=FocusSelector"))
                    .findFirst()
                    .orElseThrow();
            assertThat(event.getFormattedMessage())
                    .contains("候选人", "岗位描述", "MySQL 索引选择")
                    .doesNotContain("当前问题：", "当前回答：");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditivity);
        }
    }

    @Test
    void mainGenerationLogsFocusAndProfileWithoutTurnLevelInput() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        given(builder.build()).willReturn(client);
        given(client.prompt()).willReturn(request);
        given(request.system(any(Consumer.class))).willReturn(request);
        given(request.user(any(String.class))).willReturn(request);
        given(request.call()).willReturn(call);
        given(call.entity(GeneratedQuestion.class)).willReturn(new GeneratedQuestion("请说明一次事务隔离问题的处理过程。"));

        Logger logger = (Logger) LoggerFactory.getLogger(InterviewAiService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        boolean previousAdditivity = logger.isAdditive();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        try {
            InterviewAiService service = new InterviewAiService(builder, new DefaultResourceLoader(), "test-model");
            service.generateMainQuestion(MainQuestionGenerationContext.builder()
                    .area("MYSQL")
                    .reason("首题考察事务隔离")
                    .previousMainQuestions(java.util.List.of())
                    .focus("事务隔离下的并发更新")
                    .candidateName("候选人")
                    .resumeSummary("简历摘要")
                    .projects(java.util.List.of(new com.linj6.interviewagent.ai.api.ProjectContext("项目", "项目摘要")))
                    .positionTitle("Java 工程师")
                    .positionDescription("岗位描述")
                    .build());
            ILoggingEvent event = appender.list.stream()
                    .filter(value -> value.getFormattedMessage().contains("capability=MainQuestionGeneration"))
                    .findFirst()
                    .orElseThrow();
            assertThat(event.getFormattedMessage())
                    .contains(
                            "事务隔离下的并发更新",
                            "候选人",
                            "岗位描述",
                            "当前 PlanItem area：MYSQL",
                            "当前 PlanItem reason：首题考察事务隔离",
                            "previousMainQuestions：[]");
            var actualUserPrompt = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(request).user(actualUserPrompt.capture());
            assertThat(actualUserPrompt.getValue()).contains("MYSQL", "首题考察事务隔离", "previousMainQuestions：[]");
            assertThat(event.getFormattedMessage()).doesNotContain("当前问题", "当前回答", "Evaluation");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditivity);
        }
    }
}

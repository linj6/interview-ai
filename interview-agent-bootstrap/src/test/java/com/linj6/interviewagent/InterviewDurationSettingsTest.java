package com.linj6.interviewagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.linj6.interviewagent.interview.application.InterviewDurationSettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InterviewDurationSettingsTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(InterviewDurationSettings.class);

    @Test
    void acceptsPositiveDurationMinutes() {
        contextRunner.withPropertyValues("app.interview.duration-minutes=45").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(InterviewDurationSettings.class).durationMinutes())
                    .isEqualTo(45L);
        });
    }

    @Test
    void rejectsZeroDurationMinutesDuringStartup() {
        contextRunner.withPropertyValues("app.interview.duration-minutes=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void rejectsNegativeDurationMinutesDuringStartup() {
        contextRunner.withPropertyValues("app.interview.duration-minutes=-30").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }
}

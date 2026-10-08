package com.linj6.interviewagent;

import com.linj6.interviewagent.web.RequestIdFilter;
import java.time.Clock;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan(
        basePackages = {
            "com.linj6.interviewagent.candidate.infrastructure.persistence.mapper",
            "com.linj6.interviewagent.position.infrastructure.persistence.mapper",
            "com.linj6.interviewagent.interview.infrastructure.persistence.mapper",
            "com.linj6.interviewagent.evaluation.infrastructure.persistence.mapper"
        })
public class InterviewAgentApplication {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestIdFilter());
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    public static void main(String[] args) {
        SpringApplication.run(InterviewAgentApplication.class, args);
    }
}

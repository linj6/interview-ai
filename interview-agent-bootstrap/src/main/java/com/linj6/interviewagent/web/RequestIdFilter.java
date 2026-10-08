package com.linj6.interviewagent.web;

import com.linj6.interviewagent.common.RequestIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为每个请求生成服务端 requestId，并在请求结束时清理 MDC。 */
public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(RequestIdContext.MDC_KEY, requestId);
        response.setHeader(RequestIdContext.HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIdContext.MDC_KEY);
        }
    }
}

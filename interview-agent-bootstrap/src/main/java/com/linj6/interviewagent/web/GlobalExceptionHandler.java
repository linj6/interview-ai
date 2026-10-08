package com.linj6.interviewagent.web;

import com.linj6.interviewagent.common.RequestIdContext;
import com.linj6.interviewagent.common.api.CommonErrorCode;
import com.linj6.interviewagent.common.api.ErrorCode;
import com.linj6.interviewagent.common.api.Result;
import com.linj6.interviewagent.common.exception.BusinessException;
import com.linj6.interviewagent.evaluation.domain.EvaluationErrorCode;
import com.linj6.interviewagent.interview.domain.InterviewErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 将业务、参数、基础设施和未知异常统一转换为 Result。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.error(
                "业务异常 requestId={} errorCode={} errorMessage={}",
                RequestIdContext.current(),
                errorCode.getCode(),
                exception.getMessage(),
                exception);
        return response(errorCode, exception.getMessage(), statusFor(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(CommonErrorCode.PARAM_ERROR.getMessage());
        return response(
                CommonErrorCode.PARAM_ERROR,
                CommonErrorCode.PARAM_ERROR.getMessage() + "：" + message,
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleTypeError(Exception exception) {
        return response(CommonErrorCode.PARAM_TYPE_ERROR, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(Exception exception) {
        return response(CommonErrorCode.PARAM_MISSING, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(Exception exception) {
        return response(CommonErrorCode.REQUEST_METHOD_NOT_SUPPORT, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDatabase(DataAccessException exception) {
        log.error(
                "数据库异常 requestId={} failureType={}",
                RequestIdContext.current(),
                exception.getClass().getSimpleName(),
                exception);
        return response(CommonErrorCode.DB_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception exception) {
        log.error(
                "未处理异常 requestId={} failureType={}",
                RequestIdContext.current(),
                exception.getClass().getSimpleName(),
                exception);
        return response(CommonErrorCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode, HttpStatus status) {
        return response(errorCode, errorCode.getMessage(), status);
    }

    private ResponseEntity<Result<Void>> response(ErrorCode errorCode, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(Result.failure(errorCode, null, message));
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        if (isAny(
                errorCode,
                CommonErrorCode.PARAM_ERROR,
                CommonErrorCode.PARAM_MISSING,
                CommonErrorCode.PARAM_TYPE_ERROR,
                CommonErrorCode.REQUEST_BODY_MISSING)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (errorCode == CommonErrorCode.UNAUTHORIZED) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (errorCode == CommonErrorCode.FORBIDDEN) {
            return HttpStatus.FORBIDDEN;
        }
        if (isAny(
                errorCode,
                InterviewErrorCode.INTERVIEW_NOT_FOUND,
                InterviewErrorCode.QUESTION_NOT_FOUND,
                InterviewErrorCode.ANSWER_NOT_FOUND,
                EvaluationErrorCode.EVALUATION_NOT_FOUND,
                EvaluationErrorCode.EVALUATION_PROFILE_NOT_FOUND)) {
            return HttpStatus.NOT_FOUND;
        }
        if (isAny(
                errorCode,
                InterviewErrorCode.INTERVIEW_STATE_CONFLICT,
                InterviewErrorCode.INTERVIEW_VERSION_CONFLICT,
                InterviewErrorCode.INTERVIEW_EXPIRED,
                InterviewErrorCode.QUESTION_STATE_CONFLICT,
                InterviewErrorCode.QUESTION_INTERVIEW_MISMATCH,
                InterviewErrorCode.ANSWER_ALREADY_EXISTS,
                EvaluationErrorCode.EVALUATION_ALREADY_EXISTS)) {
            return HttpStatus.CONFLICT;
        }
        if (isAny(
                errorCode,
                EvaluationErrorCode.EVALUATION_PROPOSAL_INVALID,
                EvaluationErrorCode.EVALUATION_CALCULATION_ERROR)) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        if (isAny(
                errorCode,
                CommonErrorCode.THIRD_PARTY_ERROR,
                CommonErrorCode.AI_SERVICE_ERROR,
                CommonErrorCode.AI_RESPONSE_INVALID)) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (errorCode == CommonErrorCode.AI_SERVICE_TIMEOUT) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (errorCode == CommonErrorCode.RATE_LIMIT) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private boolean isAny(ErrorCode actual, ErrorCode... candidates) {
        for (ErrorCode candidate : candidates) {
            if (actual == candidate) {
                return true;
            }
        }
        return false;
    }
}

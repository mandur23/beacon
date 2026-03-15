package com.example.beacon.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * REST API 전역 에러 응답 형식.
 * fieldErrors 는 입력값 검증 실패 시에만 포함된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int     status,
        String  error,
        String  message,
        String  path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ApiErrorResponse ofValidation(int status, String error, String message,
                                                String path, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }
}

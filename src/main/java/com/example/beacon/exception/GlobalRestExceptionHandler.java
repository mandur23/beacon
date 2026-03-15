package com.example.beacon.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * {@link RestController} 계층에서 발생하는 예외를 일관된 {@link ApiErrorResponse} 형식으로 변환한다.
 * Thymeleaf {@code @Controller}는 대상에서 제외해 Spring 기본 에러 페이지가 유지된다.
 */
@RestControllerAdvice(annotations = RestController.class)
@Slf4j
public class GlobalRestExceptionHandler {

    /** 리소스를 찾을 수 없음 → 404 */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    /** Bean Validation(@Valid) 실패 → 400 + 필드별 오류 목록 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ApiErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        ApiErrorResponse body = ApiErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "입력값 검증에 실패했습니다.",
                req.getRequestURI(),
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /** 잘못된 요청 본문(JSON 파싱 실패) → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleBadJson(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "요청 본문을 파싱할 수 없습니다.", req);
    }

    /** 경로 변수·파라미터 타입 불일치 → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "파라미터 '" + ex.getName() + "'의 값이 올바르지 않습니다.";
        return build(HttpStatus.BAD_REQUEST, msg, req);
    }

    /** 명시적 잘못된 인자 → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    /** 그 외 미처리 예외 → 500 (내부 메시지는 로그에만 기록) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception [{}]: {}", req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", req);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────

    private static ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest req) {

        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI());

        return ResponseEntity.status(status).body(body);
    }
}

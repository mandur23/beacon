package com.example.beacon.exception;

/**
 * 요청한 리소스를 DB에서 찾을 수 없을 때 던진다.
 * {@link GlobalRestExceptionHandler}가 HTTP 404 로 매핑한다.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** "리소스명 not found: {id}" 형태의 메시지를 자동 생성한다. */
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}

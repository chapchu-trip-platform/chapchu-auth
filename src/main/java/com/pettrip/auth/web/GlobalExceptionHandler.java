package com.pettrip.auth.web;

import com.pettrip.auth.oauth2.RegistrationTokenService.InvalidRegistrationTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 이 서버에는 예외 핸들러가 아예 없었다. 그래서 온보딩에서 무엇이 잘못됐든 전부 500이 나갔다.
 *
 * <pre>
 * 토큰 형식 오류   → 500
 * 서명 불일치      → 500
 * 토큰 만료        → 500
 * 이미 가입된 계정 → 500
 * 닉네임 중복      → 500
 * </pre>
 *
 * <p>프론트는 "닉네임이 중복이니 다시 입력하세요"와 "세션이 만료됐으니 다시 로그인하세요"를 구분할 수 없었다. 신규 유저가 처음 만나는 화면이라 체감이 크다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 형식 오류·서명 불일치·만료를 모두 401로 모은다. 어느 쪽인지 밖에 알려주면 토큰을 추측하는 데 도움이 된다. */
  @ExceptionHandler(InvalidRegistrationTokenException.class)
  public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidRegistrationTokenException e) {
    log.info("registration token 검증 실패: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse("INVALID_REGISTRATION_TOKEN", "가입 토큰이 유효하지 않습니다. 다시 로그인해주세요."));
  }

  /** google_user_id / email / nickname 에 걸린 UNIQUE 위반. 대부분 이미 가입했거나 닉네임이 겹친 경우다. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException e) {
    log.info("가입 중 제약 위반: {}", e.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("ALREADY_REGISTERED", "이미 가입되었거나 사용 중인 닉네임입니다."));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .orElse("요청 값이 올바르지 않습니다.");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse("INVALID_REQUEST", message));
  }
}

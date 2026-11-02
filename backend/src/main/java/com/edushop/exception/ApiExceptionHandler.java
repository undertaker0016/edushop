package com.edushop.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ProblemDetail> statusException(ResponseStatusException exception, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
		return response(status, safeMessage(exception, status), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
		ProblemDetail problem = detail(HttpStatus.BAD_REQUEST, "Thông tin gửi lên chưa hợp lệ. Vui lòng kiểm tra các trường.", request);
		Map<String, String> fields = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
		problem.setProperty("errors", fields);
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class,
			org.springframework.web.bind.MissingRequestValueException.class})
	ResponseEntity<ProblemDetail> malformedRequest(Exception exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, "Tham số hoặc nội dung gửi lên chưa hợp lệ.", request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException exception, HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, "Dữ liệu đang bị trùng hoặc còn được sử dụng.", request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ProblemDetail> notFound(NoResourceFoundException exception, HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, "Không tìm thấy đường dẫn hoặc dữ liệu yêu cầu.", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
		log.error("Unhandled API error for {} {} ({})", request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp lỗi ngoài dự kiến. Vui lòng thử lại sau.", request);
	}

	private static String safeMessage(ResponseStatusException exception, HttpStatus status) {
		String reason = exception.getReason();
		if (reason == null || reason.isBlank()) return switch(status) {
			case UNAUTHORIZED -> "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.";
			case FORBIDDEN -> "Bạn không có quyền thực hiện thao tác này.";
			case NOT_FOUND -> "Không tìm thấy dữ liệu yêu cầu.";
			case CONFLICT -> "Thao tác không phù hợp với trạng thái dữ liệu hiện tại.";
			default -> "Yêu cầu chưa thể hoàn tất.";
		};
		return reason;
	}
	private static ResponseEntity<ProblemDetail> response(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity.status(status).body(detail(status, message, request));
	}
	private static ProblemDetail detail(HttpStatus status, String message, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
		problem.setTitle(status.getReasonPhrase());
		problem.setType(URI.create("https://api.edushop.local/problems/" + status.value()));
		problem.setInstance(URI.create(request.getRequestURI()));
		return problem;
	}
}

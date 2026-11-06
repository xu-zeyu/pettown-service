package com.pettown.common.web.advice;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * 类名: GlobalExceptionHandler
 * 描述: 全局异常处理
 * 作者: xuzeyu
 * 创建时间: 2025/12/22
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                // isBindingFailure() 为 true 表示类型转换失败（如 Long 字段传入了非数字），
                // 此时框架默认信息晦涩，统一返回明确的“参数类型错误”
                .map(fieldError -> fieldError.isBindingFailure()
                        ? "参数类型错误：" + fieldError.getField() + " 类型不正确"
                        : fieldError.getDefaultMessage())
                .orElse("参数错误");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.build(String.valueOf(status.value()), errorMessage));
    }

    /**
     * 处理请求参数类型不匹配异常（@RequestParam / @PathVariable 类型转换失败）
     * 例如 Long 类型参数传入了非数字，返回明确的“参数类型错误”
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String requiredType = e.getRequiredType() == null ? "" : e.getRequiredType().getSimpleName();
        String errorMessage = String.format("参数类型错误：%s 应为 %s 类型", e.getName(), requiredType);
        log.debug("参数类型不匹配: {}", errorMessage);
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                new BusinessException(ErrorCodeEnum.FAIL, errorMessage)
        );
    }

    /**
     * 处理业务异常
     * 资源不存在返回 404，其他业务错误返回 400
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        // 预期内的业务失败仅在排查时记录，不输出堆栈。
        log.debug("业务异常 - 错误码: {}, 错误信息: {}", e.getCommonErrorCode().getCode(), e.getMessage());
        HttpStatus status = switch (e.getCommonErrorCode().getCode()) {
            case "404" -> HttpStatus.NOT_FOUND;
            case "429" -> HttpStatus.TOO_MANY_REQUESTS;
            case "423" -> HttpStatus.LOCKED;
            case "409", "4091" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return buildErrorResponse(status, e);
    }

    /**
     * 处理参数校验异常（@Validated 校验失败）
     * 返回 HTTP 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        log.debug("参数校验异常: {}", errorMessage);
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                new BusinessException(ErrorCodeEnum.FAIL, errorMessage)
        );
    }

    /**
     * 处理运行时异常
     * 返回 HTTP 500 Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new BusinessException(ErrorCodeEnum.FAIL, e.getMessage())
        );
    }

    /**
     * 处理其他异常
     * 返回 HTTP 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("未知异常: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                new BusinessException(ErrorCodeEnum.FAIL, e.getMessage())
        );
    }


    /**
     * 构建统一的错误响应
     */
    private ResponseEntity<Result<Void>> buildErrorResponse(HttpStatus status, BusinessException e) {
        return ResponseEntity
                .status(status)
                .body(Result.error(e));
    }
}

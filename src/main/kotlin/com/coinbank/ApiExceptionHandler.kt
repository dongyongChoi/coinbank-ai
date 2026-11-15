package com.coinbank

import com.coinbank.account.InsufficientBalanceException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 도메인 예외를 HTTP 응답으로 바꾼다. */
@RestControllerAdvice
class ApiExceptionHandler {

    /** require() 실패: 잘못된 입력 */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "잘못된 요청입니다.")

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "찾을 수 없습니다.")

    /** 입력은 올바르지만 잔액이 모자라 처리할 수 없는 경우 */
    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalance(e: InsufficientBalanceException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.message ?: "잔액이 부족합니다.")

    /** 낙관 락 충돌이 재시도 횟수를 넘겨도 풀리지 않은 경우. 요청은 반영되지 않았다. */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleConflict(e: OptimisticLockingFailureException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "같은 계좌에 요청이 몰렸습니다. 잠시 후 다시 시도해 주세요.")
}

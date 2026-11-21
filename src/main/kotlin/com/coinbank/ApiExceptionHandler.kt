package com.coinbank

import com.coinbank.account.InsufficientBalanceException
import com.coinbank.product.SoldOutException
import org.springframework.dao.ConcurrencyFailureException
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

    /** 락을 얻지 못했거나 낙관 락 충돌이 재시도로도 풀리지 않은 경우. 요청은 반영되지 않았다. */
    @ExceptionHandler(ConcurrencyFailureException::class)
    fun handleConflict(e: ConcurrencyFailureException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "같은 계좌에 요청이 몰렸습니다. 잠시 후 다시 시도해 주세요.")

    /** 선착순 한도가 다 찼다. 지금 상품 상태와 부딪힌 요청이라 409로 돌려준다. */
    @ExceptionHandler(SoldOutException::class)
    fun handleSoldOut(e: SoldOutException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message ?: "마감된 상품입니다.")
}

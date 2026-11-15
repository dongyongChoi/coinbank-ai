package com.coinbank.transfer

import com.coinbank.IntegrationTest
import com.coinbank.account.AccountService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals

/**
 * 테스트에 @Transactional을 붙이지 않는다. 송금이 실제로 커밋되거나 롤백된 뒤의
 * DB 상태를 SQL로 직접 확인하기 위해서다.
 */
@IntegrationTest
class TransferApiTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val accountService: AccountService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `송금하면 201이고 잔액과 송금 기록이 DB에 남는다`() {
        val from = accountService.open("김코인", 10_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        postTransfer(from, to, 3_000).andExpect {
            status { isCreated() }
            jsonPath("$.transferId") { isNumber() }
            jsonPath("$.amount") { value(3000) }
        }

        assertEquals(7_000, balanceInDb(from))
        assertEquals(3_000, balanceInDb(to))
        assertEquals(1, transferCountInDb(from))
        assertEquals(7_000, ledgerSumInDb(from))
        assertEquals(3_000, ledgerSumInDb(to))
    }

    @Test
    fun `잔액이 부족하면 422이고 아무것도 바뀌지 않는다`() {
        val from = accountService.open("김코인", 1_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        postTransfer(from, to, 1_001).andExpect {
            status { isEqualTo(422) }
        }

        assertEquals(1_000, balanceInDb(from))
        assertEquals(0, balanceInDb(to))
        assertEquals(0, transferCountInDb(from))
    }

    @Test
    fun `받는 계좌가 없으면 404이고 이미 한 출금도 롤백된다`() {
        val from = accountService.open("김코인", 1_000).accountNumber

        postTransfer(from, "000000000000", 500).andExpect {
            status { isNotFound() }
        }

        assertEquals(1_000, balanceInDb(from))
        assertEquals(0, transferCountInDb(from))
        assertEquals(1_000, ledgerSumInDb(from))
    }

    @Test
    fun `같은 계좌로 보내면 400`() {
        val from = accountService.open("김코인", 1_000).accountNumber

        postTransfer(from, from, 500).andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `금액이 0 이하면 400`() {
        val from = accountService.open("김코인", 1_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        postTransfer(from, to, 0).andExpect {
            status { isBadRequest() }
        }
    }

    private fun postTransfer(from: String, to: String, amount: Long): ResultActionsDsl =
        mockMvc.post("/transfers") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"fromAccountNumber": "$from", "toAccountNumber": "$to", "amount": $amount}"""
        }

    private fun balanceInDb(accountNumber: String): Long? =
        jdbcTemplate.queryForObject(
            "SELECT balance FROM account WHERE account_number = ?",
            Long::class.java,
            accountNumber,
        )

    private fun ledgerSumInDb(accountNumber: String): Long? =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM ledger_entry WHERE account_number = ?",
            Long::class.java,
            accountNumber,
        )

    private fun transferCountInDb(fromAccountNumber: String): Int? =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transfer WHERE from_account_number = ?",
            Int::class.java,
            fromAccountNumber,
        )
}

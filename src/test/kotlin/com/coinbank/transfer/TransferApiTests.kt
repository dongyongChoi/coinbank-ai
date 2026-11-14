package com.coinbank.transfer

import com.coinbank.account.AccountService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 테스트가 끝나면 롤백해서 개발 DB에 데이터를 남기지 않는다
class TransferApiTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val accountService: AccountService,
) {

    @Test
    fun `송금하면 201이고 보낸 쪽은 줄고 받는 쪽은 늘어난다`() {
        val from = accountService.open("김코인", 10_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        postTransfer(from, to, 3_000).andExpect {
            status { isCreated() }
            jsonPath("$.transferId") { isNumber() }
            jsonPath("$.amount") { value(3000) }
        }

        assertEquals(7_000, balanceOf(from))
        assertEquals(3_000, balanceOf(to))
    }

    @Test
    fun `잔액이 부족하면 422이고 잔액은 그대로다`() {
        val from = accountService.open("김코인", 1_000).accountNumber
        val to = accountService.open("이뱅크", 0).accountNumber

        postTransfer(from, to, 1_001).andExpect {
            status { isEqualTo(422) }
        }

        assertEquals(1_000, balanceOf(from))
        assertEquals(0, balanceOf(to))
    }

    @Test
    fun `받는 계좌가 없으면 404`() {
        val from = accountService.open("김코인", 1_000).accountNumber

        postTransfer(from, "000000000000", 500).andExpect {
            status { isNotFound() }
        }
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

    private fun balanceOf(accountNumber: String): Long? = accountService.find(accountNumber)?.balance
}

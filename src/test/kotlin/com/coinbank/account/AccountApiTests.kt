package com.coinbank.account

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 테스트가 끝나면 롤백해서 개발 DB에 데이터를 남기지 않는다
class AccountApiTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val accountService: AccountService,
) {

    @Test
    fun `계좌를 개설하면 201과 계좌번호를 돌려준다`() {
        mockMvc.post("/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ownerName": "김코인", "initialBalance": 10000}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.accountNumber") { value(org.hamcrest.Matchers.matchesPattern("3333\\d{8}")) }
            jsonPath("$.ownerName") { value("김코인") }
            jsonPath("$.balance") { value(10000) }
        }
    }

    @Test
    fun `개설한 계좌를 계좌번호로 조회한다`() {
        val opened = accountService.open("이뱅크", 5000)

        mockMvc.get("/accounts/${opened.accountNumber}").andExpect {
            status { isOk() }
            jsonPath("$.ownerName") { value("이뱅크") }
            jsonPath("$.balance") { value(5000) }
        }
    }

    @Test
    fun `없는 계좌를 조회하면 404`() {
        mockMvc.get("/accounts/000000000000").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `예금주 이름이 비어 있으면 400`() {
        mockMvc.post("/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ownerName": " "}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `초기 잔액이 음수면 400`() {
        mockMvc.post("/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"ownerName": "김코인", "initialBalance": -1}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}

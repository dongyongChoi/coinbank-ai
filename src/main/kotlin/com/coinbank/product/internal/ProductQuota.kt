package com.coinbank.product.internal

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

/**
 * 상품마다 남은 자리를 Redis 숫자 하나로 들고 있다. 키는 "product:{상품 ID}:remaining"이다.
 * 자리를 가져가는 일(take)은 Redis 안에서 한 번에 끝나서, 두 요청이 같은 자리를 가져갈 수 없다.
 */
@Component
class ProductQuota(
    private val redis: StringRedisTemplate,
) {

    /** 새 상품의 남은 자리를 한도로 채운다. */
    fun open(productId: Long, quota: Int) {
        redis.opsForValue().set(key(productId), quota.toString())
    }

    /** 자리를 하나 가져간다. 가져가면 true, 남은 자리가 없으면 false. */
    fun take(productId: Long): Boolean =
        when (redis.execute(TAKE, listOf(key(productId)))) {
            TAKEN -> true
            SOLD_OUT -> false
            // Redis가 비워졌거나 상품을 만들 때 기록이 빠졌다. M3-3에서 가입 기록으로 다시 채운다.
            else -> throw IllegalStateException("Redis에 남은 자리 정보가 없습니다. 상품: $productId")
        }

    private fun key(productId: Long) = "product:$productId:remaining"

    companion object {
        private const val TAKEN = 1L
        private const val SOLD_OUT = 0L

        /**
         * "남은 자리 확인 → 하나 빼기"를 Redis 안에서 한 번에 한다. Redis는 스크립트 하나를 끝까지 실행한 뒤에
         * 다음 명령을 받으므로, 확인과 빼기 사이에 다른 요청이 끼어들 수 없다.
         * 자리가 0이면 빼지 않는다. 그래서 남은 자리는 음수가 되지 않는다.
         */
        private val TAKE = RedisScript.of<Long>(
            """
            local remaining = redis.call('GET', KEYS[1])
            if not remaining then return -1 end
            if tonumber(remaining) <= 0 then return 0 end
            redis.call('DECR', KEYS[1])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}

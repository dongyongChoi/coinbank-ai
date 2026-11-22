package com.coinbank.notification.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, String> {

    /**
     * 처리 표시를 넣는다. 이미 있으면 아무것도 넣지 않는다. 넣었으면 1, 이미 있었으면 0을 돌려준다.
     * save()를 쓰지 않는 이유: 기본 키를 직접 정한 엔티티의 save()는 먼저 SELECT로 있는지 보고 INSERT한다.
     * 그 사이에 같은 이벤트가 동시에 오면 둘 다 "없다"고 볼 수 있다. INSERT IGNORE는 DB가 한 번에 판단한다.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO processed_event (event_key, processed_at) VALUES (:eventKey, NOW(6))", nativeQuery = true)
    fun insertIfAbsent(eventKey: String): Int
}

# AI 활용 기록

작업마다 AI를 어떻게 썼는지, 얼마나 걸렸는지, AI가 놓친 버그를 적는다.

## M2-1 동시 송금 테스트로 갱신 유실 재현 (2026-11-15)
- AI 사용: Claude Code가 테스트(`ConcurrentTransferTests`)를 작성하고 실행했다.
- 소요 시간:
- AI가 놓친 버그:

## M2-2 복식 부기 원장과 "원장 합산 = 잔액" 검사 (2026-11-15)
- AI 사용: Claude Code가 `LedgerEntry`, `LedgerEntryRepository`, `LedgerAudit`, 테스트(`LedgerTests`)를 작성하고 실행했다.
- 소요 시간:
- AI가 놓친 버그: 첫 컴파일에서 `JdbcTemplate.queryForList`가 `List<Long?>`를 돌려주는 것을 놓쳐 타입 오류가 났다. `filterNotNull()`로 고쳤다.

## M2-3 비관 락 + 계좌 번호 정렬로 데드락 방지 (2026-11-15)
- AI 사용: Claude Code가 `AccountService.moveMoney`, `findWithLockByAccountNumber`, 양방향 동시 송금 테스트를 작성하고 실행했다.
- 소요 시간:
- AI가 놓친 버그:

## M2-4 낙관 락(@Version), 재시도, 락 전략 설정 (2026-11-15)
- AI 사용: Claude Code가 `Account.version`, `LockStrategy`, `TransferService`의 재시도, 전략별 동시 송금 테스트를 작성하고 실행했다. 재시도 횟수도 Claude Code가 측정했다.
- 소요 시간:
- AI가 놓친 버그:

## M2-5 Redis 분산 락(Redisson) (2026-11-15)
- AI 사용: Claude Code가 `AccountLock`, `RedissonConfiguration`, Redis 컨테이너 테스트 설정을 작성하고 실행했다. 락을 트랜잭션 안에 둔 경우의 충돌 횟수도 Claude Code가 실험했다.
- 다시 작업: 처음에는 Claude Code가 Redisson 클라이언트만 넣고 설정 클래스를 직접 썼다. 익숙한 Spring Data Redis 설정 방식(`redisson-spring-boot-starter`, `spring.data.redis.*`)으로 바꾸라고 요청해 커밋을 되돌리고 다시 했다.
- 소요 시간:
- AI가 놓친 버그: 스타터는 앱이 뜰 때 Redis에 연결한다. 로컬에 Redis가 떠 있어서 테스트로는 드러나지 않았고, 닫힌 포트로 돌려 본 뒤에야 확인됐다. 지연 연결 설정을 넣어 해결했다.

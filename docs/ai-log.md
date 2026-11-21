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

## M2-6 Idempotency-Key 헤더 + 유니크 제약 (2026-11-16)
- AI 사용: Claude Code가 `Transfer.idempotencyKey`, `TransferService`의 멱등 처리, 테스트 6개를 작성하고 실행했다. 유니크 제약을 뺀 실험도 Claude Code가 했다.
- 소요 시간:
- AI가 놓친 버그:

## M2-7 otel-lgtm 관측과 k6 송금 시나리오 (2026-11-17)
- AI 사용: Claude Code가 docker-compose의 otel-lgtm·k6 서비스, OTLP 설정, `k6/transfer.js`를 작성했다. 앱을 띄워 300건 시험 실행을 하고 지표와 트레이스가 Grafana에 들어온 것까지 Claude Code가 확인했다.
- 소요 시간:
- AI가 놓친 버그: 테스트에서는 스프링 부트가 지표 내보내기를 알아서 끈다고 가정했는데 아니었다. 테스트 로그에 내보내기 실패 경고가 찍힌 것을 보고 `@IntegrationTest`에서 직접 껐다.

## M2-8 락 3안 1만 건 측정, 비교표, ADR (2026-11-18)
- AI 사용: Claude Code가 측정 스크립트(`k6/bench-locks.sh`)를 작성해 6번의 측정을 돌리고, ADR 003·004 초안과 ADR 001 측정 결과를 썼다. ADR의 결정 이유는 직접 쓴다.
- 소요 시간:
- AI가 놓친 버그: 측정 스크립트가 macOS 기본 bash(3.2)에서 빈 배열 때문에 `unbound variable`로 멈췄다. 300건 시험 실행에서 발견해 고쳤다.

## M2-9 원장 합산만(ADR 001의 B안) 구현과 측정 (2026-11-18)
- AI 사용: Claude Code가 `BalanceModel`, B안의 송금·잔액 조회, 테스트(`LedgerOnlyBalanceTests`)를 작성하고 B·C를 측정했다. ADR 001 결과와 ADR 003·004의 결정 이유도 요청에 따라 Claude Code가 썼다.
- 소요 시간:
- AI가 놓친 버그:
- 참고: B안, 계좌 100개의 첫 측정에서 테스트가 한 번 11초 동안 멈췄다. 원인을 찾지 못해 다시 쟀다.

## M3-1 특판 적금 가입 API와 초과 개설 재현 (2026-11-21)
- AI 사용: Claude Code가 product 모듈(`ProductService`, `SavingsProduct`, `Subscription`, `ProductController`)과 테스트(`ConcurrentSubscriptionTests`)를 작성하고 실행했다.
- 소요 시간:
- AI가 놓친 버그: 처음 테스트는 상품 1개에 100명이 몰리는 경우만 봤는데, 10번 중 4번만 초과 개설이 났다. 같은 몰림을 상품 10개에 일으키도록 바꿔 매번 잡히게 했다.

## M3-2 Redis 원자적 한도 차감 (2026-11-21)
- AI 사용: Claude Code가 `ProductQuota`(Lua 스크립트로 "확인 후 빼기")를 작성해 `ProductService`에 연결하고, 꺼 두었던 동시 가입 테스트를 켜서 3번 돌렸다.
- 소요 시간:
- AI가 놓친 버그: 테스트 파일을 스크립트로 고칠 때 주석 들여쓰기가 달라 `@Import`가 빠졌다. 고친 뒤 파일을 다시 열어 보고 찾았다.
- 확인: Redis가 꺼진 상태에서도 Redis를 쓰지 않는 테스트가 모두 통과했다(처음 쓸 때 연결).

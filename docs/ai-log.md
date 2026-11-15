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

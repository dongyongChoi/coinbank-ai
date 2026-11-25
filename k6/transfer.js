// 동시 송금 부하 테스트.
//
// 실행 (앱이 ./gradlew bootRun 으로 떠 있어야 한다):
//   docker compose run --rm k6 run /scripts/transfer.js
//   docker compose run --rm -e ACCOUNTS=2 -e VUS=100 k6 run /scripts/transfer.js
//
// 환경 변수:
//   ACCOUNTS    계좌 수 (기본 100). 적을수록 같은 계좌에 요청이 몰린다.
//   VUS         동시에 요청을 보내는 가상 사용자 수 (기본 50)
//   ITERATIONS  전체 송금 건수 (기본 10000)
//   REPLAY_RATE 같은 Idempotency-Key로 한 번 더 보내는 비율 (기본 0.05)
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCOUNTS = Number(__ENV.ACCOUNTS || 100);
const VUS = Number(__ENV.VUS || 50);
const ITERATIONS = Number(__ENV.ITERATIONS || 10000);
const REPLAY_RATE = Number(__ENV.REPLAY_RATE || 0.05);

const AMOUNT = 1000;
// 한 계좌가 모든 송금을 혼자 보내도 잔액이 모자라지 않을 만큼 넣어 둔다.
const INITIAL_BALANCE = AMOUNT * ITERATIONS;
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    transfers: {
      // 가상 사용자들이 정해진 건수를 나눠서 처리한다. "송금 1만 건을 동시에"에 맞는 방식이다.
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '10m',
    },
  },
  thresholds: {
    // 하나라도 어긋나면 테스트 실패로 표시한다.
    checks: ['rate==1'],
    // 요약에 송금 요청만의 응답 시간(p95)을 따로 보여 주게 한다.
    'http_req_duration{kind:transfer}': ['p(95)>=0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

// 테스트 시작 전에 한 번: 계좌를 만든다.
export function setup() {
  const accounts = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const res = http.post(
      `${BASE_URL}/accounts`,
      JSON.stringify({ ownerName: `k6-${i}`, initialBalance: INITIAL_BALANCE }),
      { headers: JSON_HEADERS, tags: { kind: 'setup' } },
    );
    if (res.status !== 201) {
      throw new Error(`계좌 개설 실패: ${res.status} ${res.body}`);
    }
    accounts.push(res.json('accountNumber'));
  }
  // runId는 실행마다 달라서, 다시 돌려도 Idempotency-Key가 겹치지 않는다.
  return { accounts, runId: `k6-${Date.now()}` };
}

// 송금 한 건. 가상 사용자들이 이 함수를 ITERATIONS번 나눠 실행한다.
export default function (data) {
  const { accounts, runId } = data;
  const from = Math.floor(Math.random() * accounts.length);
  // from과 겹치지 않는 계좌를 고른다.
  const to = (from + 1 + Math.floor(Math.random() * (accounts.length - 1))) % accounts.length;

  const body = JSON.stringify({
    fromAccountNumber: accounts[from],
    toAccountNumber: accounts[to],
    amount: AMOUNT,
  });
  const key = `${runId}-${exec.scenario.iterationInTest}`;
  const params = (kind) => ({ headers: { ...JSON_HEADERS, 'Idempotency-Key': key }, tags: { kind } });

  const res = http.post(`${BASE_URL}/transfers`, body, params('transfer'));
  check(res, { '송금 201': (r) => r.status === 201 });

  // 앱에 연결조차 못 했으면(kill-test.sh에서 앱을 죽인 동안) 1초 쉬고 다음 송금으로 간다.
  // 쉬지 않으면 연결 거부가 바로 돌아와서 남은 송금 횟수를 몇 초 만에 다 써 버린다.
  if (res.status === 0) sleep(1);

  // 일부 요청은 응답을 못 받은 클라이언트처럼 같은 키로 한 번 더 보낸다.
  if (res.status === 201 && Math.random() < REPLAY_RATE) {
    const again = http.post(`${BASE_URL}/transfers`, body, params('replay'));
    check(again, {
      '재전송은 같은 송금 번호': (r) => r.status === 201 && r.json('transferId') === res.json('transferId'),
    });
  }
}

// 테스트가 끝난 뒤 한 번: 돈이 생기거나 사라지지 않았는지 확인한다.
export function teardown(data) {
  let total = 0;
  for (const accountNumber of data.accounts) {
    const res = http.get(`${BASE_URL}/accounts/${accountNumber}`, { tags: { kind: 'teardown' } });
    total += res.json('balance');
  }
  const expected = INITIAL_BALANCE * data.accounts.length;
  check(total, { '전체 잔액 합이 처음과 같다': (t) => t === expected });
  console.log(`전체 잔액 합: ${total} (기대: ${expected})`);
}

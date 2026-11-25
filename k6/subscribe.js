// 특판 오픈 부하 테스트: 한 상품에 가입이 한꺼번에 몰린다.
//
// 실행 (앱이 떠 있어야 한다):
//   docker compose run --rm k6 run /scripts/subscribe.js
//   docker compose run --rm -e ACCOUNTS=5000 -e QUOTA=100 -e VUS=300 k6 run /scripts/subscribe.js
//
// 환경 변수:
//   ACCOUNTS  가입하러 오는 사람 수 (기본 2000). 한 사람이 한 번씩 가입을 누른다.
//   QUOTA     선착순 한도 (기본 100)
//   VUS       동시에 요청을 보내는 가상 사용자 수 (기본 200)
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCOUNTS = Number(__ENV.ACCOUNTS || 2000);
const QUOTA = Number(__ENV.QUOTA || 100);
const VUS = Number(__ENV.VUS || 200);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 가입 성공(201)과 마감(409)을 따로 센다.
const subscribed = new Counter('subscribed');
const soldOut = new Counter('sold_out');

export const options = {
  scenarios: {
    open: {
      // 가상 사용자들이 ACCOUNTS번의 가입을 나눠서 처리한다. 모두 한꺼번에 출발한다.
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ACCOUNTS,
      maxDuration: '5m',
    },
  },
  thresholds: {
    // 응답은 가입(201) 아니면 마감(409)뿐이어야 한다.
    checks: ['rate==1'],
    // 가입은 정확히 한도만큼. 하나라도 넘거나 모자라면 실패로 표시한다.
    subscribed: [`count==${QUOTA}`],
    // 요약에 가입 요청의 응답 시간을 성공·마감으로 나눠 보여 준다.
    'http_req_duration{kind:subscribe}': ['p(95)>=0'],
    'http_req_duration{kind:subscribe,status:201}': ['p(95)>=0'],
    'http_req_duration{kind:subscribe,status:409}': ['p(95)>=0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

// 테스트 시작 전에 한 번: 가입할 사람들의 계좌와 상품을 만든다.
export function setup() {
  const accounts = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const res = http.post(`${BASE_URL}/accounts`, JSON.stringify({ ownerName: `k6-${i}` }), {
      headers: JSON_HEADERS,
      tags: { kind: 'setup' },
    });
    if (res.status !== 201) throw new Error(`계좌 개설 실패: ${res.status} ${res.body}`);
    accounts.push(res.json('accountNumber'));
  }
  const product = http.post(`${BASE_URL}/products`, JSON.stringify({ name: 'k6 특판', quota: QUOTA }), {
    headers: JSON_HEADERS,
    tags: { kind: 'setup' },
  });
  if (product.status !== 201) throw new Error(`상품 생성 실패: ${product.status} ${product.body}`);
  return { accounts, productId: product.json('productId') };
}

// 가입 한 번. i번째 반복은 i번째 사람이 누른다.
export default function (data) {
  const account = data.accounts[exec.scenario.iterationInTest];
  const res = http.post(
    `${BASE_URL}/products/${data.productId}/subscriptions`,
    JSON.stringify({ accountNumber: account }),
    { headers: JSON_HEADERS, tags: { kind: 'subscribe' } },
  );
  check(res, { '가입 201 또는 마감 409': (r) => r.status === 201 || r.status === 409 });
  if (res.status === 201) subscribed.add(1);
  if (res.status === 409) soldOut.add(1);
}

export function teardown(data) {
  console.log(`상품 ${data.productId}: 한도 ${QUOTA}, 가입하러 온 사람 ${ACCOUNTS}`);
}

#!/usr/bin/env bash
# 락 전략 3안을 같은 조건으로 측정한다.
#
# 실행 (프로젝트 루트에서, docker compose up -d 로 MySQL·Redis·otel-lgtm이 떠 있어야 한다):
#   ./k6/bench-locks.sh [결과 폴더]
#
# 측정할 때마다 로컬 MySQL의 account, transfer, ledger_entry 테이블을 비운다.
# 전략마다 앱을 새로 띄우고, 조건마다 1,000건으로 몸을 풀고(JVM 워밍업) 다시 비운 뒤 본 측정을 한다.
set -euo pipefail

OUT=${1:-build/bench}
STRATEGIES=${STRATEGIES:-"pessimistic optimistic redis"}
ACCOUNTS_LIST=${ACCOUNTS_LIST:-"100 2"}   # 100: 요청이 흩어짐, 2: 한 계좌 쌍에 몰림
VUS=${VUS:-50}
ITERATIONS=${ITERATIONS:-10000}
JAR=build/libs/coinbank-0.0.1-SNAPSHOT.jar

mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

sql() {
  docker compose exec -T mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" coinbank -N -e "$0" 2>/dev/null' "$1"
}

reset_db() {
  sql "SET FOREIGN_KEY_CHECKS=0; TRUNCATE ledger_entry; TRUNCATE transfer; TRUNCATE account; SET FOREIGN_KEY_CHECKS=1;"
}

# 측정이 끝난 뒤 돈이 틀린 곳이 있는지 DB에서 직접 센다.
check_db() {
  sql "SELECT
    (SELECT COUNT(*) FROM transfer),
    (SELECT COUNT(*) - COUNT(DISTINCT idempotency_key) FROM transfer),
    (SELECT COUNT(*) FROM account a WHERE a.balance <> (SELECT COALESCE(SUM(e.amount), 0) FROM ledger_entry e WHERE e.account_number = a.account_number)),
    (SELECT COUNT(*) FROM (SELECT transaction_id FROM ledger_entry GROUP BY transaction_id HAVING SUM(amount) <> 0) t);"
}

run_k6() { # $1 accounts, $2 iterations, $3 summary file (없으면 저장 안 함)
  local export=()  # macOS 기본 bash(3.2)는 빈 배열을 그냥 쓰면 set -u에 걸려서 아래처럼 펼친다
  [[ -n "${3:-}" ]] && export=(--summary-export "/out/$3")
  docker compose run --rm -q -v "$OUT:/out" \
    -e ACCOUNTS="$1" -e VUS="$VUS" -e ITERATIONS="$2" \
    k6 run --quiet ${export[@]+"${export[@]}"} /scripts/transfer.js > "$OUT/${3:-warmup}.log" 2>&1 || true
}

./gradlew -q bootJar

for strategy in $STRATEGIES; do
  echo "== $strategy: 앱 시작"
  java -jar "$JAR" --coinbank.lock-strategy="$strategy" > "$OUT/app-$strategy.log" 2>&1 &
  APP_PID=$!
  trap 'kill $APP_PID 2>/dev/null || true' EXIT
  curl -s --retry 60 --retry-delay 1 --retry-connrefused -o /dev/null http://localhost:8080/actuator/health

  for accounts in $ACCOUNTS_LIST; do
    name="$strategy-accounts$accounts"
    reset_db; run_k6 "$accounts" 1000
    reset_db; run_k6 "$accounts" "$ITERATIONS" "$name.json"
    echo "$(check_db)" > "$OUT/$name.db"
    echo "   $name 끝 (송금 기록, 중복 키, 어긋난 계좌, 합이 0이 아닌 거래): $(cat "$OUT/$name.db")"
  done

  kill $APP_PID; wait $APP_PID 2>/dev/null || true
done
echo "결과: $OUT"

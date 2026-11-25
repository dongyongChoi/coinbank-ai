#!/usr/bin/env bash
# 특판 오픈 부하를 여러 번 재고, 매번 DB·Redis·Kafka 쪽 결과를 직접 센다.
#
# 실행 (프로젝트 루트에서, docker compose up -d 로 MySQL·Redis·Kafka가 떠 있어야 한다):
#   ./k6/bench-subscribe.sh [결과 폴더]
#   RUNS=5 ACCOUNTS=5000 ./k6/bench-subscribe.sh
#
# 측정할 때마다 로컬 MySQL의 테이블과 Redis를 비운다. 앱은 한 번 띄우고,
# 작은 부하로 몸을 푼(JVM 워밍업) 뒤 같은 조건을 RUNS번 잰다.
set -euo pipefail

OUT=${1:-build/bench-subscribe}
RUNS=${RUNS:-3}
ACCOUNTS=${ACCOUNTS:-2000}
QUOTA=${QUOTA:-100}
VUS=${VUS:-200}
JAR=build/libs/coinbank-0.0.1-SNAPSHOT.jar

mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

sql() {
  # 비밀번호를 MYSQL_PWD로 넘겨 경고를 없앤다. 오류 메시지는 버리지 않는다(버리면 쿼리 오류가 빈 값으로만 보인다).
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" coinbank -N -e "$0"' "$1"
}

reset_all() {
  sql "SET FOREIGN_KEY_CHECKS=0;
    TRUNCATE ledger_entry; TRUNCATE transfer; TRUNCATE account;
    TRUNCATE subscription; TRUNCATE savings_product;
    TRUNCATE event_publication; TRUNCATE notification; TRUNCATE processed_event;
    SET FOREIGN_KEY_CHECKS=1;"
  docker compose exec -T redis redis-cli FLUSHDB > /dev/null
}

# 순서: 가입 기록 수, Redis 남은 자리, 못 보낸 이벤트, 알림을 만든 가입 이벤트 수(processed_event)
check_all() {
  local product_id
  product_id=$(sql "SELECT MAX(id) FROM savings_product;")
  local counts
  counts=$(sql "SELECT
    (SELECT COUNT(*) FROM subscription WHERE product_id = $product_id),
    (SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL),
    (SELECT COUNT(*) FROM processed_event WHERE event_key LIKE 'subscription-created:%');")
  local remaining
  remaining=$(docker compose exec -T redis redis-cli GET "product:$product_id:remaining" | tr -d '\r')
  read -r subs incomplete processed <<< "$counts"
  echo "$subs $remaining $incomplete $processed"
}

# 이벤트가 Kafka를 거쳐 알림이 될 때까지 기다린다(최대 60초).
wait_events() {
  for _ in $(seq 1 60); do
    read -r subs _ incomplete processed <<< "$(check_all)"
    [[ "$incomplete" == 0 && "$processed" == "$subs" ]] && return 0
    sleep 1
  done
}

run_k6() { # $1 accounts, $2 quota, $3 summary 이름(없으면 저장 안 함)
  local export=()
  [[ -n "${3:-}" ]] && export=(--summary-export "/out/$3.json")
  docker compose run --rm -q -v "$OUT:/out" \
    -e ACCOUNTS="$1" -e QUOTA="$2" -e VUS="$VUS" \
    k6 run --quiet ${export[@]+"${export[@]}"} /scripts/subscribe.js > "$OUT/${3:-warmup}.log" 2>&1 || true
}

./gradlew -q bootJar

echo "== 앱 시작"
java -jar "$JAR" > "$OUT/app.log" 2>&1 &
APP_PID=$!
trap 'kill $APP_PID 2>/dev/null || true' EXIT
curl -s --retry 60 --retry-delay 1 --retry-connrefused -o /dev/null http://localhost:8080/actuator/health

reset_all; run_k6 200 10; wait_events

for run in $(seq 1 "$RUNS"); do
  name="subscribe-run$run"
  reset_all; run_k6 "$ACCOUNTS" "$QUOTA" "$name"
  wait_events
  check_all > "$OUT/$name.db"
  echo "   $name 끝 (가입 기록, Redis 남은 자리, 못 보낸 이벤트, 알림이 된 가입 이벤트): $(cat "$OUT/$name.db")"
done

kill $APP_PID; wait $APP_PID 2>/dev/null || true
echo "결과: $OUT"

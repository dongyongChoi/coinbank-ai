#!/usr/bin/env bash
# 송금을 퍼붓는 도중 앱을 kill -9로 죽였다가 다시 켜고, 이벤트가 하나도 사라지지 않았는지 센다.
#
# 실행 (프로젝트 루트에서, docker compose up -d 로 MySQL·Redis·Kafka가 떠 있어야 한다):
#   ./k6/kill-test.sh [결과 폴더]
#   RUNS=5 KILL_AFTER=8 ./k6/kill-test.sh
#
# 확인하는 것: 송금 수 = 기록된 이벤트 수 = 알림이 된 이벤트 수(processed_event), 못 보낸 이벤트 0, 알림 = 송금 x 2.
# 재시작 직후 못 보낸 이벤트 수도 남긴다. 0이면 "보내기 전에 죽은 이벤트"가 없었던 것이라 그 회차는 증명이 약하다.
set -euo pipefail

OUT=${1:-build/kill-test}
RUNS=${RUNS:-3}
KILL_AFTER=${KILL_AFTER:-5}      # k6를 시작하고 몇 초 뒤에 죽일지
ITERATIONS=${ITERATIONS:-5000}
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
    TRUNCATE event_publication; TRUNCATE notification; TRUNCATE processed_event;
    SET FOREIGN_KEY_CHECKS=1;"
}

# transfer-completed 토픽에 지금까지 쌓인 메시지 수(파티션별 끝 오프셋의 합)
kafka_offset() {
  docker compose exec -T kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic transfer-completed 2>/dev/null \
    | awk -F: '{s+=$3} END{print s+0}'
}

start_app() { # $1 로그 파일
  java -jar "$JAR" > "$1" 2>&1 &
  APP_PID=$!
  curl -s --retry 60 --retry-delay 1 --retry-connrefused -o /dev/null http://localhost:8080/actuator/health
}

# 순서: 송금, 기록된 이벤트, 못 보낸 이벤트, 알림이 된 송금 이벤트, 알림 수, 원장과 잔액이 다른 계좌
check_all() {
  sql "SELECT
    (SELECT COUNT(*) FROM transfer),
    (SELECT COUNT(*) FROM event_publication WHERE event_type LIKE '%TransferCompleted'),
    (SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL),
    (SELECT COUNT(*) FROM processed_event WHERE event_key LIKE 'transfer-completed:%'),
    (SELECT COUNT(*) FROM notification),
    (SELECT COUNT(*) FROM account a WHERE a.balance <> (SELECT COALESCE(SUM(e.amount), 0) FROM ledger_entry e WHERE e.account_number = a.account_number));"
}

./gradlew -q bootJar
APP_PID=
trap '[[ -n "$APP_PID" ]] && kill $APP_PID 2>/dev/null || true' EXIT

for run in $(seq 1 "$RUNS"); do
  name="kill-run$run"
  reset_all
  start_app "$OUT/$name-app-before.log"
  offset_before=$(kafka_offset)

  docker compose run --rm -q -e ACCOUNTS=100 -e VUS=50 -e ITERATIONS="$ITERATIONS" -e REPLAY_RATE=0 \
    k6 run --quiet /scripts/transfer.js > "$OUT/$name-k6.log" 2>&1 &
  K6_PID=$!

  sleep "$KILL_AFTER"
  kill -9 "$APP_PID"; wait "$APP_PID" 2>/dev/null || true
  read -r transfers_at_kill _ incomplete_at_kill _ <<< "$(check_all)"
  echo "   $name: ${KILL_AFTER}초 뒤 kill -9 (그때까지 송금 $transfers_at_kill, 못 보낸 이벤트 $incomplete_at_kill)"

  sleep 3
  start_app "$OUT/$name-app-after.log"
  wait "$K6_PID" || true

  # 다시 보내기(30초 넘은 이벤트를 10초마다)와 알림까지 기다린다. 최대 120초.
  for _ in $(seq 1 120); do
    read -r transfers events incomplete processed _ _ <<< "$(check_all)"
    [[ "$incomplete" == 0 && "$processed" == "$transfers" ]] && break
    sleep 1
  done
  result=$(check_all)
  produced=$(( $(kafka_offset) - offset_before ))
  echo "$result $incomplete_at_kill $produced" > "$OUT/$name.db"
  echo "   $name 끝 (송금, 기록된 이벤트, 못 보낸 이벤트, 알림이 된 송금 이벤트, 알림 수, 잔액이 틀린 계좌, kill 때 못 보낸 이벤트, Kafka에 쌓인 메시지): $(cat "$OUT/$name.db")"

  kill "$APP_PID"; wait "$APP_PID" 2>/dev/null || true
  APP_PID=
done
echo "결과: $OUT"

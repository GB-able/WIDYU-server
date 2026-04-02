#!/bin/bash
# @Indexed 제거 전/후 Redis 성능 비교
# Usage: ./benchmark_indexed.sh [before|after]

MODE="${1:-both}"
ITERATIONS=1000

echo "========================================"
echo "  Redis @Indexed 벤치마크"
echo "  반복 횟수: $ITERATIONS"
echo "========================================"

# 기존 데이터 초기화
redis-cli FLUSHDB > /dev/null

run_before() {
  echo ""
  echo "── BEFORE (@Indexed 있음) ──────────────"

  # @Indexed 있을 때 동작 시뮬레이션
  # 저장: HSET + SADD (latitude 역인덱스) + SADD (longitude 역인덱스) + SADD (마스터)
  START=$(date +%s%N)
  for i in $(seq 1 $ITERATIONS); do
    LAT="37.$(( RANDOM % 9000 + 1000 ))"
    LNG="126.$(( RANDOM % 9000 + 1000 ))"

    redis-cli HSET "senior_location:$i" \
      latitude "$LAT" longitude "$LNG" updatedAt "2026-04-02T00:00:00" > /dev/null

    # @Indexed가 생성하는 역인덱스 Set
    redis-cli SADD "senior_location:latitude:$LAT" "$i" > /dev/null
    redis-cli SADD "senior_location:longitude:$LNG" "$i" > /dev/null
    redis-cli SADD "senior_location" "$i" > /dev/null
  done
  END=$(date +%s%N)
  SAVE_MS=$(( (END - START) / 1000000 ))
  echo "저장 ${ITERATIONS}회: ${SAVE_MS}ms  (평균 $(echo "scale=2; $SAVE_MS / $ITERATIONS" | bc)ms/회)"

  # 마스터 Set 크기 확인
  MASTER_SIZE=$(redis-cli SCARD "senior_location")
  LAT_KEY_COUNT=$(redis-cli --no-auth-warning KEYS "senior_location:latitude:*" | wc -l | tr -d ' ')
  LNG_KEY_COUNT=$(redis-cli --no-auth-warning KEYS "senior_location:longitude:*" | wc -l | tr -d ' ')
  echo "생성된 키: latitude 역인덱스 ${LAT_KEY_COUNT}개, longitude 역인덱스 ${LNG_KEY_COUNT}개"

  # 조회: findAllBySeniorIdIn 시뮬레이션 (SMEMBERS → HGETALL)
  START=$(date +%s%N)
  for i in $(seq 1 100); do
    redis-cli SMEMBERS "senior_location" > /dev/null
    redis-cli HGETALL "senior_location:$i" > /dev/null
  done
  END=$(date +%s%N)
  QUERY_MS=$(( (END - START) / 1000000 ))
  echo "SMEMBERS+HGETALL 100회: ${QUERY_MS}ms  (평균 $(echo "scale=2; $QUERY_MS / 100" | bc)ms/회)"

  # 업데이트: 위치 변경 (SREM 이전 + SADD 새 인덱스)
  START=$(date +%s%N)
  for i in $(seq 1 $ITERATIONS); do
    OLD_LAT="37.$(( RANDOM % 9000 + 1000 ))"
    NEW_LAT="37.$(( RANDOM % 9000 + 1000 ))"
    OLD_LNG="126.$(( RANDOM % 9000 + 1000 ))"
    NEW_LNG="126.$(( RANDOM % 9000 + 1000 ))"

    redis-cli HSET "senior_location:$i" latitude "$NEW_LAT" longitude "$NEW_LNG" > /dev/null
    redis-cli SREM "senior_location:latitude:$OLD_LAT" "$i" > /dev/null
    redis-cli SREM "senior_location:longitude:$OLD_LNG" "$i" > /dev/null
    redis-cli SADD "senior_location:latitude:$NEW_LAT" "$i" > /dev/null
    redis-cli SADD "senior_location:longitude:$NEW_LNG" "$i" > /dev/null
  done
  END=$(date +%s%N)
  UPDATE_MS=$(( (END - START) / 1000000 ))
  echo "업데이트 ${ITERATIONS}회: ${UPDATE_MS}ms  (평균 $(echo "scale=2; $UPDATE_MS / $ITERATIONS" | bc)ms/회)"

  TOTAL_KEYS=$(redis-cli DBSIZE)
  echo "Redis 총 키 수: $TOTAL_KEYS"

  redis-cli FLUSHDB > /dev/null
  echo "BEFORE 결과 → 저장:${SAVE_MS}ms / 업데이트:${UPDATE_MS}ms / 조회:${QUERY_MS}ms"
  echo "$SAVE_MS $UPDATE_MS $QUERY_MS"
}

run_after() {
  echo ""
  echo "── AFTER (@Indexed 제거) ──────────────"

  # @Indexed 없을 때 동작 시뮬레이션
  # 저장: HSET만
  START=$(date +%s%N)
  for i in $(seq 1 $ITERATIONS); do
    LAT="37.$(( RANDOM % 9000 + 1000 ))"
    LNG="126.$(( RANDOM % 9000 + 1000 ))"

    redis-cli HSET "senior_location:$i" \
      latitude "$LAT" longitude "$LNG" updatedAt "2026-04-02T00:00:00" > /dev/null
  done
  END=$(date +%s%N)
  SAVE_MS=$(( (END - START) / 1000000 ))
  echo "저장 ${ITERATIONS}회: ${SAVE_MS}ms  (평균 $(echo "scale=2; $SAVE_MS / $ITERATIONS" | bc)ms/회)"

  echo "생성된 키: latitude 역인덱스 0개, longitude 역인덱스 0개"

  # 조회: @Id 직접 조회 (HGETALL만)
  START=$(date +%s%N)
  for i in $(seq 1 100); do
    redis-cli HGETALL "senior_location:$i" > /dev/null
  done
  END=$(date +%s%N)
  QUERY_MS=$(( (END - START) / 1000000 ))
  echo "HGETALL 100회: ${QUERY_MS}ms  (평균 $(echo "scale=2; $QUERY_MS / 100" | bc)ms/회)"

  # 업데이트: HSET만
  START=$(date +%s%N)
  for i in $(seq 1 $ITERATIONS); do
    NEW_LAT="37.$(( RANDOM % 9000 + 1000 ))"
    NEW_LNG="126.$(( RANDOM % 9000 + 1000 ))"
    redis-cli HSET "senior_location:$i" latitude "$NEW_LAT" longitude "$NEW_LNG" > /dev/null
  done
  END=$(date +%s%N)
  UPDATE_MS=$(( (END - START) / 1000000 ))
  echo "업데이트 ${ITERATIONS}회: ${UPDATE_MS}ms  (평균 $(echo "scale=2; $UPDATE_MS / $ITERATIONS" | bc)ms/회)"

  TOTAL_KEYS=$(redis-cli DBSIZE)
  echo "Redis 총 키 수: $TOTAL_KEYS"

  redis-cli FLUSHDB > /dev/null
  echo "AFTER 결과 → 저장:${SAVE_MS}ms / 업데이트:${UPDATE_MS}ms / 조회:${QUERY_MS}ms"
  echo "$SAVE_MS $UPDATE_MS $QUERY_MS"
}

# 실행
BEFORE_RESULT=$(run_before)
AFTER_RESULT=$(run_after)

# 요약
echo ""
echo "========================================"
echo "  비교 요약 (${ITERATIONS}회 기준)"
echo "========================================"
printf "%-12s %10s %10s %10s\n" "" "저장" "업데이트" "조회(100회)"

BEFORE_NUMS=$(echo "$BEFORE_RESULT" | tail -1)
AFTER_NUMS=$(echo "$AFTER_RESULT" | tail -1)

B_SAVE=$(echo $BEFORE_NUMS | awk '{print $1}')
B_UPD=$(echo $BEFORE_NUMS | awk '{print $2}')
B_QRY=$(echo $BEFORE_NUMS | awk '{print $3}')

A_SAVE=$(echo $AFTER_NUMS | awk '{print $1}')
A_UPD=$(echo $AFTER_NUMS | awk '{print $2}')
A_QRY=$(echo $AFTER_NUMS | awk '{print $3}')

printf "%-12s %9sms %9sms %9sms\n" "BEFORE" "$B_SAVE" "$B_UPD" "$B_QRY"
printf "%-12s %9sms %9sms %9sms\n" "AFTER" "$A_SAVE" "$A_UPD" "$A_QRY"

echo ""
SAVE_DIFF=$(( B_SAVE - A_SAVE ))
UPD_DIFF=$(( B_UPD - A_UPD ))
QRY_DIFF=$(( B_QRY - A_QRY ))
printf "%-12s %9sms %9sms %9sms\n" "단축" "$SAVE_DIFF" "$UPD_DIFF" "$QRY_DIFF"
echo "========================================"

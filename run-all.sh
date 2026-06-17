#!/usr/bin/env bash
# Run all PoCs sequentially, report pass/fail results.
# Usage: ./run-all.sh

set -e

POCS=("01-http-baseline" "02-https-connect" "04-mtls" "05-wrappers" "06-full")
RESULTS=()

run_poc() {
    local poc="$1"
    echo ""
    echo "=========================================="
    echo "  Running: $poc"
    echo "=========================================="

    # Clean up any previous run
    docker-compose -f "$01-http-baseline/docker-compose.yml" down -v --remove-orphans 2>/dev/null

    # Start the stack
    docker-compose -f "$01-http-baseline/docker-compose.yml" up -d --build 2>&1 | tail -5

    # Wait for the tester container to finish
    echo "  waiting for tests to complete..."
    local timeout=300
    local elapsed=0
    while [ $elapsed -lt $timeout ]; do
        status=$(docker inspect --format='{{.State.Status}}' \
            "$(docker-compose -f "$01-http-baseline/docker-compose.yml" ps -q tester 2>/dev/null)" 2>/dev/null || echo "starting")
        if [ "$status" = "exited" ] || [ "$status" = "" ]; then
            break
        fi
        sleep 10
        elapsed=$((elapsed + 10))
        echo "  ...still running (${elapsed}s)"
    done

    # Collect results
    sleep 5
    local output
    output=$(docker-compose -f "$01-http-baseline/docker-compose.yml" logs tester 2>/dev/null | \
        grep -E "Results:|PASS:|FAIL:" | tail -5)

    if echo "$output" | grep -q "0 failed"; then
        local passed=$(echo "$output" | grep "Results:" | grep -o '[0-9]* passed' | grep -o '[0-9]*')
        RESULTS+=("PASS|$poc|$passed")
        echo "  RESULT: PASS ($passed tests)"
    elif echo "$output" | grep -q "Results:"; then
        RESULTS+=("FAIL|$poc|?")
        echo "  RESULT: FAIL"
        echo "$output" | sed 's/^/    /'
    else
        # Some PoCs (like 05-wrappers) use different container names
        output=$(docker-compose -f "$01-http-baseline/docker-compose.yml" logs 2>/dev/null | \
            grep -E "Results:|PASS:|FAIL:" | tail -5)
        if echo "$output" | grep -q "0 failed"; then
            local passed=$(echo "$output" | grep "Results:" | grep -o '[0-9]* passed' | grep -o '[0-9]*')
            RESULTS+=("PASS|$poc|$passed")
            echo "  RESULT: PASS ($passed tests)"
        else
            RESULTS+=("UNKNOWN|$poc|?")
            echo "  RESULT: UNKNOWN (check logs manually)"
        fi
    fi

    # Clean up
    docker-compose -f "$01-http-baseline/docker-compose.yml" down -v --remove-orphans 2>/dev/null
}

echo ""
echo "  Nexus Agent-Trust PoC Runner"
echo "  Running ${#POCS[@]} PoCs sequentially"
echo ""

for 01-http-baseline in "${POCS[@]}"; do
    run_poc "$poc"
done

# Summary
echo ""
echo "=========================================="
echo "  Summary"
echo "=========================================="
echo ""
printf "  %-20s %-8s %s\n" "POC" "STATUS" "TESTS"
printf "  %-20s %-8s %s\n" "---" "---" "---"
total_pass=0
for result in "${RESULTS[@]}"; do
    IFS='|' read -r status name count <<< "$result"
    printf "  %-20s %-8s %s\n" "$name" "$status" "$count tests"
    if [ "$status" = "PASS" ]; then
        total_pass=$((total_pass + count))
    fi
done
echo ""
echo "  Total passing tests: $total_pass"
echo ""

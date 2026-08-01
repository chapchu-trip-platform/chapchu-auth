#!/usr/bin/env bash
# chapchu-auth 배포 검증 스크립트
# 사용법: ./scripts/verify-auth.sh [AUTH_URL]
# 기본값: https://auth.chapchu.site

set -euo pipefail

AUTH_URL="${1:-https://auth.chapchu.site}"
PASS=0
FAIL=0
SKIP=0

ok()   { echo "  [OK]  $1"; PASS=$((PASS+1)); }
fail() { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
skip() { echo "  [SKIP] $1"; SKIP=$((SKIP+1)); }
info() { echo ""; echo "=== $1 ==="; }

# ── 1. Pod 상태 (chapchu 네임스페이스 접근 가능할 때만) ──────
info "1. Pod 상태"
if ! kubectl get namespace chapchu &>/dev/null; then
    skip "chapchu 네임스페이스 미연결 — 서버에서 실행 시 체크됨"
else
    if kubectl get pods -n chapchu 2>/dev/null | grep -q "chapchu-auth.*1/1.*Running"; then
        RESTARTS=$(kubectl get pods -n chapchu 2>/dev/null | grep chapchu-auth | awk '{print $4}')
        ok "chapchu-auth Running (재시작 횟수: ${RESTARTS})"
    else
        fail "chapchu-auth 파드가 Running 상태가 아님"
        kubectl get pods -n chapchu 2>/dev/null || true
    fi
fi

# ── 2. 헬스체크 ──────────────────────────────────────────────
info "2. Actuator Health"
HEALTH=$(curl -sf --max-time 10 "${AUTH_URL}/actuator/health" 2>/dev/null || echo "UNREACHABLE")
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    ok "Health: UP"
else
    fail "Health 응답 비정상: ${HEALTH}"
fi

# ── 3. OIDC 메타데이터 ───────────────────────────────────────
info "3. OIDC Discovery"
OIDC=$(curl -sf --max-time 10 "${AUTH_URL}/.well-known/openid-configuration" 2>/dev/null || echo "UNREACHABLE")
if echo "$OIDC" | grep -q '"issuer"'; then
    ISSUER=$(echo "$OIDC" | grep -o '"issuer":"[^"]*"' | cut -d'"' -f4)
    ok "OIDC 메타데이터 정상"
    echo "       issuer: ${ISSUER}"

    if echo "$ISSUER" | grep -q "^https://"; then
        ok "issuer가 https:// 로 시작"
    else
        fail "issuer가 https:// 가 아님 — JWT 검증 실패 가능: ${ISSUER}"
    fi
else
    fail "OIDC 메타데이터 조회 실패: ${OIDC}"
fi

# ── 4. Google OAuth redirect_uri 검증 ───────────────────────
info "4. Google OAuth redirect_uri"
# /oauth2/authorization/google 로 요청 → Google 로그인 URL로 리다이렉트
GOOGLE_URL=$(curl -sf --max-time 10 -o /dev/null \
    -w "%{redirect_url}" \
    "${AUTH_URL}/oauth2/authorization/google" 2>/dev/null || echo "UNREACHABLE")

if echo "$GOOGLE_URL" | grep -q "accounts.google.com"; then
    ok "Google 로그인 URL로 정상 리다이렉트"

    # URL 디코딩 후 redirect_uri 추출
    REDIR_URI=$(echo "$GOOGLE_URL" | grep -oE "redirect_uri=[^&]*" | head -1 \
        | sed 's/redirect_uri=//' | python3 -c "import sys,urllib.parse; print(urllib.parse.unquote(sys.stdin.read().strip()))" 2>/dev/null \
        || echo "$GOOGLE_URL" | grep -oE "redirect_uri=[^&]*" | head -1)
    echo "       redirect_uri: ${REDIR_URI}"

    if echo "$REDIR_URI" | grep -q "^https://"; then
        ok "redirect_uri가 https:// (Google mismatch 없음)"
    else
        fail "redirect_uri가 https가 아님 — Google Cloud Console 설정 불일치 발생"
    fi
else
    fail "Google 리다이렉트 실패: ${GOOGLE_URL}"
fi

# ── 결과 요약 ────────────────────────────────────────────────
echo ""
echo "==============================="
echo " 결과: 통과 ${PASS} / 실패 ${FAIL} / 건너뜀 ${SKIP}"
echo "==============================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

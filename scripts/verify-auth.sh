#!/usr/bin/env bash
# chapchu-auth 배포 검증 스크립트
# 사용법: ./scripts/verify-auth.sh [AUTH_URL]
# 기본값: https://auth.chapchu.site

set -euo pipefail

AUTH_URL="${1:-https://auth.chapchu.site}"
PASS=0
FAIL=0

ok()   { echo "  [OK]  $1"; PASS=$((PASS+1)); }
fail() { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
info() { echo ""; echo "=== $1 ==="; }

# ── 1. Pod 상태 ──────────────────────────────────────────────
info "1. Pod 상태"
if kubectl get pods -n chapchu 2>/dev/null | grep -q "chapchu-auth.*1/1.*Running"; then
    RESTARTS=$(kubectl get pods -n chapchu 2>/dev/null | grep chapchu-auth | awk '{print $4}')
    ok "chapchu-auth Running (재시작 횟수: ${RESTARTS})"
else
    fail "chapchu-auth 파드가 Running 상태가 아님"
    kubectl get pods -n chapchu 2>/dev/null || true
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

    # issuer가 https인지 확인
    if echo "$ISSUER" | grep -q "^https://"; then
        ok "issuer가 https:// 로 시작"
    else
        fail "issuer가 https:// 가 아님 — JWT 검증 실패 가능: ${ISSUER}"
    fi
else
    fail "OIDC 메타데이터 조회 실패: ${OIDC}"
fi

# ── 4. Google OAuth 리다이렉트 확인 ─────────────────────────
info "4. Google OAuth 리다이렉트"
LOGIN_REDIRECT=$(curl -sf --max-time 10 -o /dev/null -w "%{redirect_url}" \
    "${AUTH_URL}/oauth2/authorize?response_type=code&client_id=chapchu-front&redirect_uri=https://auth.chapchu.site/login/oauth2/code/google&scope=openid+profile+email&code_challenge=abc&code_challenge_method=S256" \
    2>/dev/null || echo "UNREACHABLE")

if echo "$LOGIN_REDIRECT" | grep -q "accounts.google.com"; then
    ok "Google 로그인 페이지로 정상 리다이렉트"

    # redirect_uri 파라미터가 https인지 확인
    if echo "$LOGIN_REDIRECT" | grep -qE "redirect_uri=https%3A"; then
        ok "redirect_uri가 https:// (Google mismatch 없음)"
    else
        REDIR_URI=$(echo "$LOGIN_REDIRECT" | grep -oE "redirect_uri=[^&]*" | head -1)
        fail "redirect_uri가 https가 아님: ${REDIR_URI}"
    fi
else
    fail "Google 리다이렉트 실패: ${LOGIN_REDIRECT}"
fi

# ── 결과 요약 ────────────────────────────────────────────────
echo ""
echo "==============================="
echo " 결과: 통과 ${PASS} / 실패 ${FAIL}"
echo "==============================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

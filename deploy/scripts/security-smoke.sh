#!/usr/bin/env bash
# ===========================================================================
# CampusForum 安全冒烟脚本（任务 TDEPLOY.1）
# ---------------------------------------------------------------------------
# 在部署完成后用 curl 验证以下四项加固是否在 nginx 边缘真实生效：
#   1) Knife4j / OpenAPI 文档双重屏蔽（漏洞 2 / T2.4）
#   2) actuator 兜底 404、actuator/prometheus 仅内网（漏洞 32 / T9.6）
#   3) Sa-Token 未登录访问 /api/v1/posts 返回 401（基线回归）
#   4) WS legacy token query 拒绝（漏洞 8 / WS_TICKET_ENFORCED=true 灰度后启用）
#
# 使用：
#   BASE_URL=https://your-campus.edu ./security-smoke.sh
# ===========================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost}"
PASS=0
FAIL=0

assert_status() {
    local name="$1"; local url="$2"; local expected="$3"
    local actual
    actual=$(curl -k -o /dev/null -s -w '%{http_code}' "$url" || echo "000")
    if [ "$actual" = "$expected" ]; then
        echo "[OK]  $name  ($actual)"
        PASS=$((PASS+1))
    else
        echo "[FAIL] $name  expected=$expected actual=$actual url=$url"
        FAIL=$((FAIL+1))
    fi
}

echo "===== 安全冒烟开始：$BASE_URL ====="
# 1) 接口文档边缘屏蔽
assert_status "swagger-ui 屏蔽"      "$BASE_URL/swagger-ui/index.html" "404"
assert_status "v3/api-docs 屏蔽"     "$BASE_URL/v3/api-docs"           "404"
assert_status "doc.html 屏蔽"        "$BASE_URL/doc.html"              "404"
assert_status "webjars 屏蔽"         "$BASE_URL/webjars/anything.js"   "404"

# 2) actuator
assert_status "actuator/env 兜底 404"  "$BASE_URL/actuator/env"  "404"
# Prometheus 仅内网；外部 IP 命中 deny -> 403
assert_status "actuator/prometheus 外网拒绝" "$BASE_URL/actuator/prometheus" "403"

# 3) 基线：未登录访问需要登录的 API → 401
assert_status "未登录 /api/v1/users/me → 401" "$BASE_URL/api/v1/users/me" "401"

# 4) WS token query 拒绝（仅当灰度切换 WS_TICKET_ENFORCED=true 后启用）
if [ "${TEST_WS_LEGACY_TOKEN:-0}" = "1" ]; then
    assert_status "WS token query 拒绝" "$BASE_URL/ws/notify?token=fake" "400"
fi

echo "===== 冒烟结束：通过 $PASS / 失败 $FAIL ====="
[ "$FAIL" = "0" ]

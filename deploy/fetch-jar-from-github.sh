#!/usr/bin/env bash
# 在服务器上拉取 GitHub Actions 工件「home-accounting-jar」里的 jar 并安装。
# 依赖：curl、jq、unzip、sudo（若 RESTART_SERVICE=1 或 INSTALL_DIR 需 root）
#
# 用法示例：
#   sudo apt install -y curl jq unzip
#   sudo cp deploy/fetch-jar-from-github.sh /usr/local/bin/home-accounting-fetch-jar
#   sudo chmod +x /usr/local/bin/home-accounting-fetch-jar
#   sudo cp deploy/github-fetch.env.example /etc/home-accounting/github-fetch.env
#   sudo nano /etc/home-accounting/github-fetch.env   # 填 TOKEN 与 OWNER/REPO
#   sudo chmod 600 /etc/home-accounting/github-fetch.env
#   source /etc/home-accounting/github-fetch.env && home-accounting-fetch-jar

set -euo pipefail

ENV_FILE="${ENV_FILE:-/etc/home-accounting/github-fetch.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

: "${GITHUB_TOKEN:?缺少 GITHUB_TOKEN（见 deploy/github-fetch.env.example）}"
: "${GITHUB_OWNER:?缺少 GITHUB_OWNER}"
: "${GITHUB_REPO:?缺少 GITHUB_REPO}"

BRANCH="${GITHUB_BRANCH:-main}"
INSTALL_DIR="${INSTALL_DIR:-/opt/home-accounting}"
RESTART="${RESTART_SERVICE:-1}"
UNIT="${SYSTEMD_UNIT:-home-accounting}"
WORKDIR="${WORKDIR:-/tmp/home-accounting-jar-fetch.$$}"

API="https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}"
HDR=(-H "Accept: application/vnd.github+json" -H "Authorization: Bearer ${GITHUB_TOKEN}")

cleanup() { rm -rf "$WORKDIR"; }
trap cleanup EXIT

mkdir -p "$WORKDIR"
cd "$WORKDIR"

RUN_JSON=$(curl -fsS "${HDR[@]}" \
  "${API}/actions/workflows/build-jar.yml/runs?per_page=1&branch=${BRANCH}&status=completed&conclusion=success")
RUN_ID=$(echo "$RUN_JSON" | jq -r '.workflow_runs[0].id // empty')
if [[ -z "$RUN_ID" || "$RUN_ID" == "null" ]]; then
  echo "未找到分支 ${BRANCH} 上、结论为 success 的 Build jar 运行记录。请先在 GitHub Actions 里手动跑一次 Build jar 并等它成功。" >&2
  exit 1
fi

ART_JSON=$(curl -fsS "${HDR[@]}" "${API}/actions/runs/${RUN_ID}/artifacts")
ART_ID=$(echo "$ART_JSON" | jq -r '.artifacts[] | select(.name=="home-accounting-jar") | .id' | head -1)
if [[ -z "$ART_ID" || "$ART_ID" == "null" ]]; then
  echo "运行 ${RUN_ID} 中未找到名为 home-accounting-jar 的工件。" >&2
  exit 1
fi

echo "使用运行 run_id=${RUN_ID}，artifact_id=${ART_ID}"
curl -fsSL "${HDR[@]}" -o artifact.zip "${API}/actions/artifacts/${ART_ID}/zip"
unzip -o artifact.zip
test -f home-accounting-server.jar

sudo mkdir -p "${INSTALL_DIR}"
sudo install -m 644 home-accounting-server.jar "${INSTALL_DIR}/home-accounting-server.jar"

if [[ "$RESTART" == "1" ]]; then
  sudo systemctl restart "${UNIT}"
  echo "已安装并执行: systemctl restart ${UNIT}"
else
  echo "已安装到 ${INSTALL_DIR}/home-accounting-server.jar（未重启服务，RESTART_SERVICE=${RESTART}）"
fi

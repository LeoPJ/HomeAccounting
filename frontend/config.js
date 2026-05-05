/**
 * API 根地址。发布前务必改为线上 https 地址，并在微信公众平台 → 开发 → 开发管理 →
 * 开发设置 → 服务器域名中配置 request 合法域名（含 https）。
 * 生产环境变量、GitHub CI/CD、服务器部署步骤见仓库 docs/production-and-release.md。
 * 本地调试可在开发者工具勾选「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」。
 */
module.exports = {
  API_BASE: 'http://127.0.0.1:8080',
}

# 生产环境变量与发布说明

本文档与仓库内配置一致：`backend/src/main/resources/application.yml`、`frontend/config.js`、GitHub Actions（`.github/workflows/`）、部署示例（`deploy/`）。

---

## 一、生产环境变量清单

以下环境变量由 `application.yml` 引用。**生产环境必须设置**的项已标「必填」；其余可按需覆盖默认值。


| 变量名                        | 必填    | 默认值（未设置时）        | 说明                                     |
| -------------------------- | ----- | ---------------- | -------------------------------------- |
| `SERVER_PORT`              | 否     | `8080`           | HTTP 监听端口；若前有 Nginx 反代，可仍用 8080 仅本机监听。 |
| `DB_HOST`                  | 否     | `127.0.0.1`      | MySQL 主机。                              |
| `DB_PORT`                  | 否     | `3306`           | MySQL 端口。                              |
| `DB_USER`                  | 否     | `homeaccounting` | 数据库用户。                                 |
| `DB_PASSWORD`              | **是** | 空                | 数据库密码；勿提交到 Git。                        |
| `JWT_SECRET`               | **是** | 空                | JWT 签名密钥，须足够长且随机；轮换会使已签发 token 失效。     |
| `JWT_EXPIRATION`           | 否     | `7d`             | Token 有效期（Spring Boot 时长格式）。           |
| `WECHAT_MINI_APP_ID`       | **是** | 空                | 微信小程序 AppId。                           |
| `WECHAT_MINI_APP_SECRET`   | **是** | 空                | 微信小程序 AppSecret。                       |
| `DEV_LOGIN_ENABLED`        | 否     | `false`          | 生产必须为 `false`；勿使用 `dev` profile 上线。    |
| `DEV_LOGIN_SECRET`         | 否     | 空                | 仅当开启 dev 登录时使用；生产勿配置。                  |
| `APP_CORS_ORIGIN_PATTERNS` | 否     | `*`              | 浏览器 CORS；小程序不依赖此项，管理端若用浏览器可收紧。         |


**Spring Profile**：默认不要激活 `dev`。本地开发可用 `application-dev.yml`（见该文件顶部注释）。

**JDBC**：当前 URL 使用 `useSSL=false`，与同机或内网 MySQL 常见；若连接云厂商「要求 SSL」的实例，需在运维侧改为带 SSL 参数的 URL（可后续单独加 `spring.datasource.url` 环境变量或 `application-prod.yml`，勿把密码写进 URL）。

**数据库名**：连接串中库名为 `home_accounting`，与 Flyway 一致；建库与账号权限需事先准备好。

---

## 二、服务器上的配置文件示例

- `deploy/home-accounting.env.example` → 复制为 `/etc/home-accounting.env`（权限 `600`）。
- `deploy/home-accounting.service.example` → 复制为 systemd unit，并把 `EnvironmentFile`、`ExecStart`、`User` 改成你的布局。

进程需能读取 `EnvironmentFile`；Java 启动命令与 jar 路径与 CI 上传路径一致（见下文）。

---

## 三、GitHub CI/CD

### 3.1 CI（已接入）

- 工作流：`.github/workflows/ci.yml`
- 触发：`main` / `master` 的 `push` 与指向这两支的 `pull_request`。
- 行为：在 `ubuntu-latest` 上用 Temurin 17 执行 `backend` 目录下的 `mvn -B verify`。

默认分支名若不同，请改 `ci.yml` 里的 `branches` 列表。

### 3.2 后端部署（已接入，需你配置密钥）

- 工作流：`.github/workflows/deploy-backend.yml`
- 触发：仅 **手动** `workflow_dispatch`（Actions 里点 Run workflow）。
- 行为：构建 jar → SCP 到服务器目录 → 执行你提供的远程命令（安装 jar 并重启服务）。

**GitHub 仓库 Secrets：**


| Secret                  | 说明                                                                                                                         |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `DEPLOY_HOST`           | 服务器 IP 或域名。                                                                                                                |
| `DEPLOY_USER`           | SSH 登录用户（建议专用 `deploy` 用户，仅必要目录写权限）。                                                                                       |
| `DEPLOY_SSH_KEY`        | 该用户对应的 **私钥** 全文（`-----BEGIN ...` 起止）。                                                                                     |
| `DEPLOY_REMOTE_DIR`     | SCP 目标目录（**目录**，不要带文件名）。上传后的文件名为 `home-accounting-server.jar`（与 CI 中一致）。                                                   |
| `DEPLOY_REMOTE_COMMAND` | 在服务器上执行的多行 shell，例如：`sudo install` 拷贝 jar 到运行目录 + `sudo systemctl restart home-accounting`。失败应非零退出（已 `script_stop: true`）。 |


**SSH 端口**：工作流里写死为 `22`；若你使用其它端口，请改 `deploy-backend.yml` 中两处 `port: 22`。

`**DEPLOY_REMOTE_COMMAND` 示例**（请按你的 `DEPLOY_REMOTE_DIR` 与 systemd 中的 jar 路径修改）：

```bash
sudo install -m 644 /opt/incoming/home-accounting-server.jar /opt/home-accounting/home-accounting-server.jar && sudo systemctl restart home-accounting
```

若 `scp-action` 将文件直接落在 `DEPLOY_REMOTE_DIR` 下且文件名为 `home-accounting-server.jar`，则命令里源路径应写该目录下的该文件名。

**安全建议**：为 GitHub Actions 单独生成一对部署专用 SSH 密钥，在服务器 `authorized_keys` 里限制 `command=` 或配合最小权限 sudoers，避免泄露主密钥。

---

## 四、发布从头到尾：谁做什么

### 我能（已在仓库里）替你做好的


| 项                | 说明                                       |
| ---------------- | ---------------------------------------- |
| CI               | `mvn verify` 在每次推 PR / 主分支时自动跑。          |
| 部署工作流骨架          | 构建 + SCP + SSH 执行命令；你填 Secrets 与远程脚本即可用。 |
| 环境变量清单与示例        | 本文档 + `deploy/*.example`。                |
| systemd / env 模板 | 按需复制到服务器后修改。                             |


### 需要你自己完成的事情


| 阶段             | 事项                                                                                             |
| -------------- | ---------------------------------------------------------------------------------------------- |
| 代码托管           | 在 GitHub 建仓库，推送本仓库；确认默认分支名与 `ci.yml` 一致。                                                       |
| 服务器            | 安装 Java 17、MySQL；创建库 `home_accounting` 与用户；配置防火墙（仅开放 443/80 与 SSH 等）。                          |
| 反向代理与 HTTPS    | Nginx（或 Caddy）终止 TLS，反代到本机 `127.0.0.1:8080`；申请证书。                                              |
| 环境变量           | 将 `deploy/home-accounting.env.example` 落实为 `/etc/home-accounting.env`（或等价方式），填写全部必填项。          |
| 首次部署 jar       | 可手动拷 jar 跑通一次，再接入 GitHub Actions；或直接用 `Deploy backend` workflow。                               |
| systemd        | 安装 unit，保证 `ExecStart` 指向最终 jar 路径，与 `DEPLOY_REMOTE_COMMAND` 里拷贝目标一致。                          |
| GitHub Secrets | 按上文表配置；`DEPLOY_REMOTE_COMMAND` 与服务器目录约定一致。                                                     |
| 小程序            | `frontend/config.js` 的 `API_BASE` 改为线上 `https://...`；微信公众平台配置 **request 合法域名**；真机关闭「不校验域名」测一遍。 |
| 微信与合规          | 类目、隐私指引、用户协议链接、审核说明与测试账号（如需要）。                                                                 |
| 回归             | 真机全流程：登录、记账、流水、报表、我的；弱网 / 401。                                                                 |
| 运维             | 日志轮转、数据库备份、监控告警（可选）。                                                                           |


### 推荐发布顺序（简版）

1. 服务器：MySQL + JDK + env 文件 + systemd（先手动 `java -jar` 或 `systemctl` 验证能启动）。
2. Nginx HTTPS → 用 curl 测通健康接口（如有）或任意 API。
3. 小程序改 `API_BASE` → 开发者工具与真机验证。
4. GitHub 配 Secrets → 跑一次 **Deploy backend** → 再测小程序。
5. 微信提交审核 → 通过后发布。

---

## 五、与之前「接下来要做」清单的对应关系


| 原清单项               | 状态                          |
| ------------------ | --------------------------- |
| 真机全流程回归            | **你执行**                     |
| 生产联调（config + API） | **你执行**（改 `config.js` + 域名） |
| 微信公众平台域名与审核        | **你执行**                     |
| 隐私与用户协议            | **你执行**                     |
| 后端生产配置与密钥          | **你执行**（env 文件；仓库内仅示例）      |
| 版本说明与上传            | **你执行**                     |
| Tab 图标等美术          | **你按需**                     |
| 错误监控 / 日志告警        | **你按需**接入云厂商或自建             |


若你希望 CI 在 **打 tag** 时自动部署，可在 `deploy-backend.yml` 增加 `push: tags: ['v*']` 并自行评估误触风险。
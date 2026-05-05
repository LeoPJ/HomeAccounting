# HomeAccounting

小家记账：后端为 **Spring Boot 3 + MyBatis + MySQL + Flyway**，对外提供 HTTP JSON API；微信小程序为后续前端。

---

## 开发与协作流程（已采用）

- **代码**：本机开发 → 推送 **GitHub** → 在云服务器上拉取 / CI 部署。
- **数据库**：MySQL 部署在 **云服务器**，开发期可 **本地直连同一套库** 调试（无需本机再装一份 MySQL）。
- **注意**：多人共用同一库时，注意 Flyway 迁移约定与数据操作范围；大改动前先备份。

MySQL 访问安全建议：

- 尽量不要把 **3306** 对公网全行业放行；可用 **云安全组仅放行你的出口 IP**，或使用 **SSH 隧道** 把远端端口转到本机再连。
- 应用使用 **独立数据库账号**，最小权限；**密码与密钥不要写入仓库**（曾写入 `application.yml` 的需在云上 **轮换密码**）。

---

## 仓库结构


| 路径                                         | 说明                                                         |
| ------------------------------------------ | ---------------------------------------------------------- |
| `backend/`                                 | Java 后端工程（主 `pom.xml`、启动类、Flyway、接口代码）                     |
| `pom.xml`（根目录）                             | 聚合工程；可从仓库根执行：`mvn -pl backend spring-boot:run -Plocal-dev` |
| `backend/db/bootstrap/`                    | 首次在云/MySQL 上 **建空库** 用脚本                                   |
| `backend/db/README.txt`                    | 数据库与迁移约定简述                                                 |
| `backend/src/main/resources/db/migration/` | Flyway 版本化 SQL                                             |


---

## 数据库说明（MySQL）

在 MySQL 里 `**DATABASE` 与 `SCHEMA` 同义**。本项目使用 **单库** `**home_accounting`**，业务表均在同一库内。

---

## Flyway 与首次建库

### 1）云上首次建空库（人工执行一次）

执行：`backend/db/bootstrap/00_create_databases.sql`  
效果：`CREATE DATABASE IF NOT EXISTS home_accounting …`

### 2）应用启动时自动迁移

Flyway 脚本目录：`backend/src/main/resources/db/migration/`（基线 `**V1__baseline.sql`**，增量如 `**V2__report_templates.sql`**）。**  
**JDBC 默认连接 `**home_accounting`**，历史表 `**flyway_schema_history**` 也在该库。

### 3）手工建表在先、尚无 `flyway_schema_history`（baseline）

若你已经 **手动执行 SQL 建好表**，库里还没有 `**flyway_schema_history`**，应用会因 Flyway 校验失败而无法启动。此时在配置里使用（`**backend/src/main/resources/application.yml` 已预留**）：

- `**spring.flyway.baseline-on-migrate: true`**
- `**spring.flyway.baseline-version: 1`**（与当前 `V1__baseline` 对齐）

含义：在非空库上 **只做基线登记**，不把 `V1` 再执行一遍；之后增量变更请只追加 `**V2__*.sql`**，**不要修改已发布过的旧版本文件**。

若曾用过「多个库 / 旧脚本名」等与当前 `**V1__baseline`** 不一致的历史，请勿混用同一 `**flyway_schema_history`**；必要时应 **新库重来** 或由 DBA 手工对齐迁移记录。

---

## 环境要求

- **JDK 17**
- **Maven 3.x**
- 能访问到的 **MySQL**（库 `**home_accounting`**）

---

## 本地启动（推荐：`local-dev` Maven Profile）

进入 `**backend`** 目录（或在仓库根使用 `-pl backend`）：

```powershell
cd backend
mvn spring-boot:run -Plocal-dev
```

激活 Spring `**dev**` profile 后会加载 `**backend/src/main/resources/application-dev.yml**`：

- 提供 **仅本地可用的 JWT 密钥**（勿用于生产）
- 开启 `**POST /api/v1/auth/dev/login`**，默认 `**secret`** 见下文接口章节

默认端口：**8080**（`**SERVER_PORT`** 可改）。

### 数据库连接（本地跑也必须配）

开发库在云上时，启动前例如：

```powershell
$env:DB_HOST = "你的MySQL主机"
$env:DB_PORT = "3306"
$env:DB_USER = "数据库用户名"
$env:DB_PASSWORD = "数据库密码"
```

### 非开发模式启动（接近生产）

必须配置 `**JWT_SECRET**`（UTF-8 下至少 **32 个字符**）；微信登录还需 `**WECHAT_MINI_APP_ID`** / `**WECHAT_MINI_APP_SECRET`**：

```powershell
$env:JWT_SECRET = "至少32字符的随机串"
$env:WECHAT_MINI_APP_ID = "小程序AppId"
$env:WECHAT_MINI_APP_SECRET = "小程序AppSecret"
mvn spring-boot:run
```

### PowerShell 与 Maven 参数（已踩坑）

- **错误写法**：`mvn spring-boot:run -Dspring-boot.run.profiles=dev`  
PowerShell 可能把 `-D` 拆开，出现 `**Unknown lifecycle phase ".run.profiles=dev"`**。
- **推荐**：始终使用 `**mvn spring-boot:run -Plocal-dev`**（见上文）。
- **备选**：整条加引号  
`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`  
或：  
`$env:SPRING_PROFILES_ACTIVE = "dev"; mvn spring-boot:run`

### 启动失败：`JwtService` / `JWT_SECRET`

若未配置 `**JWT_SECRET`** 且未启用 `**dev`** profile，会在 `**JwtService` `@PostConstruct`** 阶段失败，表现为 **Tomcat / ApplicationContext 启动失败**。处理方式：使用 `**-Plocal-dev`** 或设置 `**JWT_SECRET`**。

### 常用环境变量一览


| 变量                                                | 说明                | 默认（节选）                                                |
| ------------------------------------------------- | ----------------- | ----------------------------------------------------- |
| `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` | MySQL             | host `127.0.0.1`，用户 `homeaccounting`                  |
| `JWT_SECRET`                                      | JWT 签名密钥          | 空（`**-Plocal-dev`** 时由 `application-dev.yml` 提供）      |
| `JWT_EXPIRATION`                                  | Token 有效期         | `7d`                                                  |
| `WECHAT_MINI_APP_ID` / `WECHAT_MINI_APP_SECRET`   | 微信 `code2session` | 空                                                     |
| `DEV_LOGIN_ENABLED` / `DEV_LOGIN_SECRET`          | 自行开启测试登录          | `false` / 空（**dev profile** 下见 `application-dev.yml`） |
| `SERVER_PORT`                                     | HTTP 端口           | `8080`                                                |
| `APP_CORS_ORIGIN_PATTERNS`                        | CORS（浏览器调试）       | `*`                                                   |
| `SPRING_PROFILES_ACTIVE`                          | Spring Profile    | 可不设；设为 `**dev`** 等价于加载 `application-dev.yml`          |


**勿将密钥、AppSecret、数据库密码提交到 Git。** `generator.properties`（若本地复制）应在 `**.gitignore`** 中忽略。

---

## 云上部署（概要）

1. **主机**：安装 **JDK 17**，设置系统时区（建议 `**Asia/Shanghai`**）。
2. **打包**：在 `backend` 目录执行 `**mvn -q package -DskipTests`**，得到 `**target/home-accounting-server-*.jar`**。
3. **配置**：通过环境变量或运维平台注入 `**DB_*`**、`**JWT_SECRET`**、`**WECHAT_***` 等；禁止在线上使用 `**spring.profiles.active=dev**` 或 `application-dev.yml` 中的默认密钥。
4. **进程**：`java -jar …` 配合 **systemd**、**supervisor** 或容器保持常驻。
5. **入口**：前置 **Nginx / Caddy** 等做 **HTTPS** 与反向代理；防火墙一般仅放行 **443** 与 **SSH**。
6. **小程序**：正式环境必须在微信公众平台配置 **request 合法域名**（HTTPS），与后端域名一致。

---

## HTTP API 约定

- **鉴权**：除白名单外，`/api/`** 请求需 Header `**Authorization: Bearer <token>`**。**  
**白名单包括：`**GET /api/v1/ping`**、`**/api/v1/auth/****`（含测试登录与微信登录）。  
非 `/api/**` 前缀的路径当前不做 JWT 校验。
- **CORS**：已配置；浏览器调试可用。**微信小程序 `wx.request` 不依赖浏览器 CORS**，但仍需合法域名与 HTTPS。

---

## 统一响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

`code !== 0` 表示失败；HTTP 状态码常见 **400 / 401 / 403 / 409 / 500 / 502 / 503**（数据库不可用多为 **503**，乐观锁冲突多为 **409**）。

### Windows PowerShell 与 `curl`（易踩坑）

在 **PowerShell** 里，命令 `**curl` 不是真正的 curl**，而是 `**Invoke-WebRequest` 的别名**，因此 `**-H "Authorization: …"` 会报错**（`Headers` 需要哈希表，不能是整段字符串）。

**做法一：调用系统自带的 `curl.exe`（与 Linux 示例一致）**

```powershell
$token = "你的 JWT"
curl.exe -s "http://localhost:8080/api/v1/ledgers" -H "Authorization: Bearer $token"
```

**做法二：用 PowerShell 原生写法**

```powershell
$token = "你的 JWT"
$h = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/ledgers" -Headers $h -Method Get
```

下文里的 `**curl -s**` 若在 PowerShell 中执行，请改成 `**curl.exe -s**`，或使用上面的 `**Invoke-RestMethod**`。

---

## 接口一览与测试方式（当前已联调通过）

下列示例假定 `**http://localhost:8080**`。在 PowerShell 中请将 `**curl**` 换成 `**curl.exe**`（见上文）。**§7** 示例中的 `**ledgerId` / `fundAccountId` / `categoryId` / `tagIds`** 请换成你在 **§6** 创建的真实 ID。

### 1. 健康检查（无需登录）


| 项目      | 说明             |
| ------- | -------------- |
| **GET** | `/api/v1/ping` |


```powershell
curl -s http://localhost:8080/api/v1/ping
```

期望 `**data**` 中含 `**"status":"up"**`。

---

### 2. 本地测试登录（无需微信，需 `dev` profile）

需 `**mvn spring-boot:run -Plocal-dev**`（或等价启用 `**dev**` profile）。默认 `**secret**` 来自 `**application-dev.yml**`：`**local-only-dev-secret**`。


| 项目       | 说明                                                             |
| -------- | -------------------------------------------------------------- |
| **POST** | `/api/v1/auth/dev/login`                                       |
| **Body** | `secret`（必填）；`label`（可选，`[a-zA-Z0-9_-]` 1～64，默认 `**default`**） |


```powershell
curl -s -X POST http://localhost:8080/api/v1/auth/dev/login `
  -H "Content-Type: application/json" `
  -d '{"secret":"local-only-dev-secret","label":"alice"}'
```

成功时 `**data**` 含 `**token**`、`userId`、`householdId`（可为 `null`）、`needsHousehold`、`**loginMode":"dev"**`。  
库中 `**users.wechat_openid**` 形如 `**dev|alice**`。

---

### 3. 微信小程序登录


| 项目       | 说明                                             |
| -------- | ---------------------------------------------- |
| **POST** | `/api/v1/auth/wechat/login`                    |
| **Body** | `**code`**：仅能通过小程序 `**wx.login`** 获取（短时效，不可手写） |


```powershell
curl -s -X POST http://localhost:8080/api/v1/auth/wechat/login `
  -H "Content-Type: application/json" `
  -d '{"code":"从小程序端获取的js_code"}'
```

需配置 `WECHAT_MINI_APP_ID` / `WECHAT_MINI_APP_SECRET`，且与公众平台一致。开发者工具可勾选「不校验合法域名」连本地；真机/正式环境需 **HTTPS 域名**。

#### 微信小程序联调详细步骤（从注册到真机）

下面说明：`**js_code` 只能从小程序里 `wx.login` 拿到**，不能手写、不能用 Postman「凭空造」；你必须有一个 **小程序前端工程**（哪怕只有一个页面）。

##### 1. 公众平台准备账号与密钥

1. 打开 [微信公众平台](https://mp.weixin.qq.com/)，使用小程序账号登录（若还没有小程序，需先注册「小程序」类型）。
2. 左侧进入 **开发 → 开发管理 → 开发设置**。
3. 记录 **开发者 ID（AppID）**。
4. 在 **AppSecret（小程序密钥）** 处生成或重置密钥，**只显示一次**，复制保存到密码管理器；后端环境变量 `**WECHAT_MINI_APP_SECRET`** 必须与这里 **完全一致**。
5. 同一页面向下找到 **服务器域名 → request 合法域名**：填写你 **最终对外提供 HTTPS 的 API 域名**（例如 `https://api.example.com`）。
  - 域名须备案等政策要求以微信文档为准。  
  - **本地 `http://127.0.0.1:8080` 一般不能写进合法域名**；联调本地后端时依赖下一步「开发者工具里不校验域名」。

##### 2. 后端与微信对齐

1. 启动后端时设置（或写入部署环境）：
  - `WECHAT_MINI_APP_ID` = 上一步的 AppID  
  - `WECHAT_MINI_APP_SECRET` = 上一步的 AppSecret
2. 确保小程序能访问到你的登录接口完整 URL，例如：
  `https://你的域名/api/v1/auth/wechat/login`  
   若后端仅在笔记本上跑，真机无法直接访问 `localhost`，需 **内网穿透（如 ngrok、frp）+ HTTPS** 或先把后端部署到云服务器再用公网域名。

##### 3. 安装微信开发者工具并创建小程序项目

1. 下载安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)。
2. **新建项目**：选择「小程序」，**AppID** 填你在公众平台的小程序 AppID（测试号也可按官方说明使用）。
3. 在项目中任意页面（如 `pages/index`）或 `**app.js` 的 `onLaunch`** 里编写登录逻辑（见下节代码）。

##### 4. 小程序端最小代码（获取 `code` 并换 token）

`wx.login` 成功回调里的 `**res.code`** 就是后端要的 `**js_code`**（约 **5 分钟**内有效，**用过一次即作废**，每次登录应重新 `wx.login`）。

将下面里的 `**API_BASE`** 改成你的后端根地址（注意末尾不要多写 `/api` 重复）：

```javascript
// 示例：pages/index/index.js
const API_BASE = 'http://127.0.0.1:8080' // 仅模拟器 + 勾选「不校验合法域名」时可用；真机请改为 HTTPS 域名

Page({
  onLoad() {
    wx.login({
      success: (res) => {
        if (!res.code) {
          console.error('wx.login 未返回 code', res)
          return
        }
        // 开发期可临时打印，上线前删除
        console.log('js_code 已获取，即将请求后端（勿长期打印到线上）')
        wx.request({
          url: `${API_BASE}/api/v1/auth/wechat/login`,
          method: 'POST',
          header: { 'Content-Type': 'application/json' },
          data: { code: res.code },
          success: (r) => {
            const body = r.data
            if (body.code !== 0) {
              wx.showToast({ title: body.message || '登录失败', icon: 'none' })
              return
            }
            wx.setStorageSync('token', body.data.token)
            wx.showToast({ title: '登录成功', icon: 'success' })
          },
          fail: (err) => {
            console.error(err)
            wx.showToast({ title: '请求失败', icon: 'none' })
          }
        })
      },
      fail: (err) => {
        console.error('wx.login 失败', err)
      }
    })
  }
})
```

后端返回体与当前工程一致时为：`{ code, message, data: { token, userId, householdId, needsHousehold } }`（无 `loginMode` 字段）。

##### 5. 开发者工具里必开的开关（连本地 HTTP 时）

1. 打开 **详情 → 本地设置**。
2. 勾选 **「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**。
3. 重新编译后，在 **调试器 → Network** 里应能看到对 `/api/v1/auth/wechat/login` 的 **POST** 及响应状态码。

若仍失败：看 **Console** 与 **Network** 里响应 JSON（例如微信返回的 `errcode` 经后端转成 `message`）。

##### 6. 真机预览 / 体验版 / 正式版

1. **关闭**「不校验合法域名」进行真机测试（与线上一致）。
2. 在公众平台把 **request 合法域名** 配成你的 **HTTPS API 域名**。
3. 后端证书链完整、TLS 版本符合微信要求。
4. 使用开发者工具 **预览** 或上传代码为 **体验版**，用手机微信扫码。

##### 7. 常见错误对照


| 现象                       | 常见原因                                                                    |
| ------------------------ | ----------------------------------------------------------------------- |
| 后端返回「微信登录失败: **40029**」等 | `code` **已过期或已用过**；或 **AppSecret / AppID 与当前小程序不一致**；或 `code` 来自别的小程序实例 |
| `invalid appsecret`      | 密钥错误或未更新到后端环境变量                                                         |
| 请求发不出去 / 不在合法域名          | 未配置合法域名，且未勾选「不校验」；或真机访问了 `http://localhost`                             |
| 后端 502 / 上游错误            | 本机无法访问微信接口（极少见）；或后端解析微信 JSON 异常                                         |


更完整的错误码见：[微信官方文档 - 小程序登录](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html)。

---

### 4. 当前用户（需登录）


| 项目         | 说明                                  |
| ---------- | ----------------------------------- |
| **GET**    | `/api/v1/me`                        |
| **Header** | `**Authorization: Bearer <token>`** |


```powershell
$token = "粘贴登录返回的token"
curl -s http://localhost:8080/api/v1/me -H "Authorization: Bearer $token"
```

期望 `**data.userId**` 为当前用户；缺 Token 或无效返回 **401**。

---

### 5. 家庭（需登录）

同一用户在同一时刻只能属于一个家庭（表 `**household_members`** 对 `**user_id`** 唯一）。


| 方法       | 路径                        | 说明                                                         |
| -------- | ------------------------- | ---------------------------------------------------------- |
| **GET**  | `/api/v1/households/me`   | 当前用户家庭信息；未加入时 `**data.joined`** 为 `**false`**              |
| **POST** | `/api/v1/households`      | 创建家庭并成为 **OWNER**；Body：`{ "name": "小家" }`                  |
| **POST** | `/api/v1/households/join` | 凭邀请码加入；Body：`{ "inviteCode": "XXXXXXXX" }`（大小写不敏感，服务端会规范化） |


成功时 `**data`** 含 `**joined`、`householdId`、`name`、`inviteCode`、`role`**。

```powershell
$token = "登录返回的 token"
curl -s http://localhost:8080/api/v1/households/me -H "Authorization: Bearer $token"

curl -s -X POST http://localhost:8080/api/v1/households `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d "{\"name\":\"小家\"}"

curl -s -X POST http://localhost:8080/api/v1/households/join `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d "{\"inviteCode\":\"ABCDEFGH\"}"
```

**小程序端**：`frontend/config.js` 中的 `**API_BASE`** 与开发者工具「不校验合法域名」；登录后若 `**needsHousehold`** 或 `**/households/me`** 未加入，会进入 `**pages/household/household`** 创建或加入家庭。

---

### 6. 账本 / 资金账号 / 分类 / 标签（需登录且须已加入家庭）

以下接口均按 **当前用户所在 `household_id`** 过滤或写入；**不可**在 Body 里伪造别人的家庭 ID。未加入家庭时调用会返回 **403**（「请先创建或加入家庭」）。

**账本** `/api/v1/ledgers`


| 方法     | 路径                     | 说明                         |
| ------ | ---------------------- | -------------------------- |
| GET    | `/api/v1/ledgers`      | 列表                         |
| GET    | `/api/v1/ledgers/{id}` | 详情                         |
| POST   | `/api/v1/ledgers`      | 创建；Body：`{ "name": "日常" }` |
| PUT    | `/api/v1/ledgers/{id}` | 改名；Body：`{ "name": "…" }`  |
| DELETE | `/api/v1/ledgers/{id}` | 删除（若已有流水关联可能失败）            |


**资金账号 `/api/v1/fund-accounts`**


| 方法     | 路径                           | 说明                                                                        |
| ------ | ---------------------------- | ------------------------------------------------------------------------- |
| GET    | `/api/v1/fund-accounts`      | 列表                                                                        |
| GET    | `/api/v1/fund-accounts/{id}` | 详情                                                                        |
| POST   | `/api/v1/fund-accounts`      | 创建；Body：`{ "name": "现金", "initialBalance": 0 }`（`initialBalance` 可选，默认 0） |
| PUT    | `/api/v1/fund-accounts/{id}` | **仅改名**；余额只能通过记账流水变更                                                      |
| DELETE | `/api/v1/fund-accounts/{id}` | 删除（若已被流水引用可能失败）                                                           |


**分类 `/api/v1/categories`**


| 方法     | 路径                                | 说明                                                                      |
| ------ | --------------------------------- | ----------------------------------------------------------------------- |
| GET    | `/api/v1/categories?type=EXPENSE` | 列表；`type` 可选：`EXPENSE`                                                  |
| GET    | `/api/v1/categories/{id}`         | 详情                                                                      |
| POST   | `/api/v1/categories`              | 创建；Body：`{ "type":"EXPENSE","name":"餐饮","sortOrder":0,"enabled":true }` |
| PUT    | `/api/v1/categories/{id}`         | 部分更新；Body 可含 `name` / `sortOrder` / `enabled`                           |
| DELETE | `/api/v1/categories/{id}`         | 删除（若已被流水引用可能失败）                                                         |


**标签 `/api/v1/tags`**


| 方法     | 路径                  | 说明                         |
| ------ | ------------------- | -------------------------- |
| GET    | `/api/v1/tags`      | 列表                         |
| GET    | `/api/v1/tags/{id}` | 详情                         |
| POST   | `/api/v1/tags`      | 创建；Body：`{ "name": "出差" }` |
| PUT    | `/api/v1/tags/{id}` | 改名                         |
| DELETE | `/api/v1/tags/{id}` | 删除（若已被流水引用可能失败）            |


示例（需带 `**Authorization: Bearer**`）：

```powershell
$token = "登录后的 token"

curl -s http://localhost:8080/api/v1/ledgers -H "Authorization: Bearer $token"

curl -s -X POST http://localhost:8080/api/v1/categories `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d "{\"type\":\"EXPENSE\",\"name\":\"餐饮\",\"sortOrder\":10,\"enabled\":true}"
```

---

### 7. 记账流水（需登录且须已加入家庭）

与 `**transactions**` / `**transaction_tags**` / `**fund_accounts.balance**` 同一事务更新；资金账号使用 `**version**` 乐观锁，并发更新失败时返回 **409**（`code: 409`）。`**fundAccountId`** 可为 `**null`**（仅记账、不计入任一账户余额）。

`**/api/v1/transactions`**


| 方法     | 路径                          | 说明                                                                                                                                    |
| ------ | --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/v1/transactions`      | 列表；可选 `**ledgerId`**；时间范围 `**occurredFrom` / `occurredTo`**（ISO-8601 日期时间）；`**limit**` 默认 50、最大 200；按 `**occurred_at DESC, id DESC**` |
| GET    | `/api/v1/transactions/{id}` | 详情（含 `**tagIds**`）                                                                                                                    |
| POST   | `/api/v1/transactions`      | 创建；Body 见下                                                                                                                            |
| PUT    | `/api/v1/transactions/{id}` | 全量更新（与 POST 相同字段语义；可 `**fundAccountId: null**` 解除账户关联）                                                                                |
| DELETE | `/api/v1/transactions/{id}` | 删除（自动撤销对应账户余额影响；`**transaction_tags**` 随删）                                                                                            |


**POST / PUT Body（JSON）**


| 字段              | 说明                                                     |
| --------------- | ------------------------------------------------------ |
| `ledgerId`      | 账本 ID                                                  |
| `fundAccountId` | 资金账号 ID，可选；缺省或 `**null`** 表示不计余额                       |
| `categoryId`    | 分类 ID（须与本家庭一致，且 `**categories.type`** 与 `**type`** 一致） |
| `type`          | `**EXPENSE`**（支出）或 `**INCOME`**（收入）                    |
| `amount`        | 金额，**正数**，四位小数；支出时从账户扣减，收入时增加                          |
| `occurredAt`    | 发生时间（ISO-8601）                                         |
| `note`          | 备注，可选                                                  |
| `tagIds`        | 标签 ID 数组，可选；重复 ID 会去重                                  |


成功时 `**data`** 为流水对象，字段与库表一致并额外含 `**tagIds`: number[]**。

```powershell
$token = "登录后的 token"

curl.exe -s "http://localhost:8080/api/v1/transactions?limit=20" -H "Authorization: Bearer $token"

curl.exe -s -X POST http://localhost:8080/api/v1/transactions `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d "{\"ledgerId\":1,\"fundAccountId\":1,\"categoryId\":1,\"type\":\"EXPENSE\",\"amount\":12.5,\"occurredAt\":\"2026-05-04T10:00:00\",\"note\":\"午餐\",\"tagIds\":[1,2]}"
```

---

### 8. 报表模板与聚合查询（需登录且须已加入家庭）

将「筛选条件 + 聚合维度 + 指标 + 排序」用 **JSON 定义**保存为多套模板；执行时服务端 **仅按白名单拼装 SQL**，动态部分一律 **预编译参数绑定**，不提供任意 SQL 文本入口。

**数据库**：Flyway `**V2__report_templates.sql`** 建表 `**report_templates`**（按 `**household_id`** 隔离，同一家庭下 `**name**` 唯一）。

#### 8.1 模板 CRUD：`/api/v1/report-templates`


| 方法     | 路径                              | 说明                                       |
| ------ | ------------------------------- | ---------------------------------------- |
| GET    | `/api/v1/report-templates`      | 模板列表（含 `**definition**` JSON）            |
| GET    | `/api/v1/report-templates/{id}` | 详情                                       |
| POST   | `/api/v1/report-templates`      | 创建；Body：`name` + `**definition**`        |
| PUT    | `/api/v1/report-templates/{id}` | 更新；至少提供 `**name**` 或 `**definition**` 之一 |
| DELETE | `/api/v1/report-templates/{id}` | 删除                                       |


#### 8.2 DSL 白名单：`GET /api/v1/reports/schema`

返回 `**ReportSchemaResponse**`（版本号、各类上限、`filterFields` / `dimensions` / `metricFunctions`、排序方向、收支枚举、`notes`）。与 `**ReportSchemaCatalog**`、校验器 `**ReportDefinitionValidator**`、`**ReportSqlBuilder**` **同源**，供小程序或管理端动态表单渲染；**无需手写死白名单文档**。

```powershell
curl.exe -s http://localhost:8080/api/v1/reports/schema -H "Authorization: Bearer $token"
```

#### 8.3 执行查询：`POST /api/v1/reports/run`

Body（`**templateId**` 与 `**definition**` 二选一，勿同时使用）：


| 字段             | 说明                                                                |
| -------------- | ----------------------------------------------------------------- |
| `templateId`   | 使用已保存模板；必须属于当前用户所在家庭                                              |
| `definition`   | 不显式存模板，直接内联一整份定义（结构与模板 JSON 相同）                                   |
| `extraFilters` | 可选；在最终定义上 **追加** 一组 `**filters`**（AND），常用于运行时传入「本月」「某一账本」等而不改模板本体 |


响应 `**data`**：`{ "rows": [ { ...列名... }, ... ], "limit": number }`，列名即维度字段名或指标的 `**alias`**。

#### 8.4 `definition` JSON 结构（模板存库与 `/reports/run` 内联相同）

```json
{
  "filters": [
    { "field": "occurred_at", "op": "between", "params": ["2026-05-01T00:00:00", "2026-05-31T23:59:59"] },
    { "field": "type", "op": "eq", "params": ["EXPENSE"] },
    { "field": "ledger_id", "op": "in", "params": [1, 2] }
  ],
  "dimensions": ["month", "category_id"],
  "metrics": [
    { "alias": "total_amount", "fn": "sum", "field": "amount" },
    { "alias": "tx_cnt", "fn": "count_rows" }
  ],
  "sort": [
    { "key": "month", "dir": "ASC" },
    { "key": "total_amount", "dir": "DESC" }
  ],
  "limit": 500
}
```

**上限（服务端硬限制）**：`**filters`≤24**，`**dimensions`≤12**，`**metrics`≤16**（至少 1 项），`**sort`≤12**，`**limit`** 默认 **500**、最大 **2000**。

`**filters.field`（白名单）**：`occurred_at`，`type`，`ledger_id`，`fund_account_id`，`category_id`，`created_by`，`tag_id`。

`**filters.op`**：


| field             | 允许的 op                                                                            |
| ----------------- | --------------------------------------------------------------------------------- |
| `occurred_at`     | `eq` `ne` `gt` `gte` `lt` `lte` `between`（2 个时间字符串）；另支持 `is_null` / `is_not_null` |
| `type`            | `eq` `ne` `in`；参数取值须 `**EXPENSE`** / `**INCOME`**                                 |
| `ledger_id` 等 ID  | `eq` `ne` `in`                                                                    |
| `fund_account_id` | 同上，并额外支持 `**is_null`** / `**is_not_null`**                                        |
| `tag_id`          | `**eq`** `**ne`** `**in`**（EXISTS 子查询，不手写 JOIN）                                   |


时间字符串格式：**ISO-8601 本地时间**，如 `**2026-05-04T10:00:00`**。

`**dimensions`（白名单）**：`ledger_id`，`category_id`，`fund_account_id`，`type`，`created_by`，`tag_id`，`month`（`YYYY-MM`），`day`（日期），`year`。**  
**若包含 `**tag_id`**，服务端会自动 `**JOIN transaction_tags**`；一笔流水多个标签时，按标签聚合会使金额在多行重复计入（常见「标签维度」语义）。

`**metrics**`：


| `fn`                          | 含义                                                               | `field`         |
| ----------------------------- | ---------------------------------------------------------------- | --------------- |
| `sum` / `min` / `max` / `avg` | 对流水 `**amount**`                                                 | 必填 `**amount**` |
| `count_rows`                  | 有 `**tag_id**` 维度时用 `**COUNT(DISTINCT t.id)**`，否则 `**COUNT(*)**` | 省略              |
| `count_distinct_tx`           | `**COUNT(DISTINCT t.id)**`                                       | 省略              |


`**metrics.alias**`：字母开头，字母数字下划线，长度 ≤64；全局唯一。

`**sort.key**`：必须是某个 `**dimensions**` 字段名，或某个指标的 `**alias**`；`**dir**` 为 `**ASC**` / `**DESC**`。

**扩展方式**：新增可用字段、运算符或聚合类型须在服务端 `**ReportSchemaCatalog`**（及 `**ReportDefinitionValidator`** / `**ReportSqlBuilder**`）同步维护（不接受客户端拼接原始 SQL）；`**GET /reports/schema**` 会随代码一并更新。

```powershell
$token = "登录后的 token"

curl.exe -s -X POST http://localhost:8080/api/v1/report-templates `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d "{\"name\":\"按月分类支出\",\"definition\":{\"filters\":[{\"field\":\"type\",\"op\":\"eq\",\"params\":[\"EXPENSE\"]}],\"dimensions\":[\"month\",\"category_id\"],\"metrics\":[{\"alias\":\"total\",\"fn\":\"sum\",\"field\":\"amount\"},{\"alias\":\"cnt\",\"fn\":\"count_rows\"}],\"sort\":[{\"key\":\"month\",\"dir\":\"ASC\"},{\"key\":\"total\",\"dir\":\"DESC\"}],\"limit\":200}}"

curl.exe -s -X POST http://localhost:8080/api/v1/reports/run `
  -H "Authorization: Bearer $token" -H "Content-Type: application/json" `
  -d "{\"templateId\":1,\"extraFilters\":[{\"field\":\"occurred_at\",\"op\":\"between\",\"params\":[\"2026-05-01T00:00:00\",\"2026-05-31T23:59:59\"]}]}"
```

---

## 联调记录（当前已验证）


| 项目          | 说明                                                                                                                                                                                                                         |
| ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **环境与端到端**  | 配置 `**DB_*`**、`-Plocal-dev` 启动后：`**POST /api/v1/auth/dev/login`** → `**GET /api/v1/households/me**` → `**GET /api/v1/ledgers**` / **§7** / **§8** 报表均可正常返回 **200 / code 0**。数据库异常时接口返回 **503** 及排查提示（**dev** profile 附详情）。 |
| **微信登录**    | 小程序模拟器（开发者工具）勾选「不校验合法域名」、`wx.login` → `POST /api/v1/auth/wechat/login`，**HTTP 200**，鉴权成功，`token` 可写入本地存储。                                                                                                                  |
| **后端调用微信**  | 微信 `jscode2session` 返回体为 JSON，但响应头常为 `**Content-Type: text/plain`**。若直接用 `RestClient` 反序列化为对象，会报 `**UnknownContentTypeException`**。当前实现为：**先按字符串读取响应，再用 Jackson 解析**（见 `WeChatMiniProgramClient`）。                         |
| **家庭流程**    | 前端 `**utils/request.js`** 自动带 Bearer；首页登录后 `**needsHousehold`** 或未加入则跳转 `**pages/household`**；创建/加入成功后 `**reLaunch`** 回首页并展示家庭名称、邀请码、角色。**已在开发者工具模拟器测通。**                                                                  |
| **主数据 API** | 账本 / 资金账号 / 分类 / 标签 CRUD（**§6**）、**记账流水（§7）**、**报表（§8，含 `GET /reports/schema`）** 已实现，权限绑定 `**household_id`**。可用 curl + Bearer 或小程序对接。                                                                                      |
| **小程序 MVP** | `**app.json` TabBar** + **账本 / 记一笔 / 报表 / 设置** 页面已对接上述接口；**报表**依赖后端至少存在一个 `**report_templates`** 模板（可用 README §8 `curl` 创建）。本地 `**config.js`** 的 `**API_BASE`** + 开发者工具「不校验合法域名」。                                          |


### 小程序工程约定（参考）

- 仓库内建议使用 `**frontend/`** 放置小程序源码（与 `backend/` 并列）。
- 自建 Spring Boot API 时，创建项目选 **「不使用云服务」**，不必开通微信云开发。
- 本地调试：`API_BASE` 指向 `http://127.0.0.1:8080`（或你的本机 IP）+ **详情 → 本地设置 → 不校验合法域名**。

---

## 下一步开发建议（推荐顺序）

以下为从产品闭环出发的推进顺序；**当前 MVP 闭环已在仓库内打通**，后续按优先级迭代即可。

1. **前端基础能力**
  - **已完成**：`**frontend/utils/request.js`** 自动 `**Authorization: Bearer`**；401 清 token、`**reLaunch`** 回 `**pages/index/index**` 并提示。  
  - **已完成**：登录后 `**needsHousehold`** / `**/households/me`** 分流至 `**pages/household`**；首页 `**onShow`** 带 token 时刷新家庭信息。
2. **后端业务 API（与 MVP 对齐）**
  - **已完成**：**家庭**；**账本 / 资金账号 / 分类 / 标签** CRUD（**§6**）；**记账流水**（**§7**）；**报表**（**§8**：模板、`**/reports/run`**、`**GET /reports/schema`**，白名单集中于 `**ReportSchemaCatalog**`）。  
  - **可选增强**：更多维度/运算符、分页游标、异步导出、缓存热点模板。
3. **小程序页面**
  - **已完成**：底部 **Tab**（首页 / 账本 / 记一笔 / 报表 / 设置）；**设置**页维护分类与标签并展示资金账号；**报表**页拉 `**/reports/schema`**、列模板并「本月」运行 `**/reports/run`**；记一笔对接 `**/transactions**`。Tab 图标暂共用 `**frontend/images/tab/item.png**`（可替换为独立 PNG）。  
  - **真机与上线**：**HTTPS** + **request 合法域名**（本地模拟器仍可不校验域名）。
4. **运维与发布**
  - 云服务器 jar、Nginx、证书；GitHub 与 CI 按团队习惯。

---

## MyBatis 代码生成

1. 复制 `**backend/src/main/resources/generator/generator.properties.example`** 为 `**generator.properties`**（勿提交真实密码）。
2. 在 `**backend`** 目录执行：

```powershell
mvn mybatis-generator:generate
```

---

## 常见问题（本会话中已验证）


| 现象                                                        | 处理                                                                                                                                                                                                                                                                                                   |
| --------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Maven 报 `**Unknown lifecycle phase ".run.profiles=dev"**` | 使用 `**mvn spring-boot:run -Plocal-dev**`，或对 `-D` 整条加引号 / 使用 `**SPRING_PROFILES_ACTIVE**`                                                                                                                                                                                                             |
| 启动失败且与 `**JwtService` / init** 相关                         | 配置 `**JWT_SECRET`**（≥32 字符）或使用 `**-Plocal-dev`**                                                                                                                                                                                                                                                     |
| Flyway 报非空库无 `**flyway_schema_history**`                  | 使用 `**baseline-on-migrate**` + `**baseline-version: 1**`（见上文 Flyway 小节）                                                                                                                                                                                                                              |
| `**mvn spring-boot:run` 找不到 spring-boot 插件**              | 在 `**backend`** 目录执行，或 `**mvn -f backend/pom.xml …`**                                                                                                                                                                                                                                                |
| 微信登录后端日志 `**UnknownContentTypeException`**（`text/plain`）  | 微信接口 JSON 常带 `**text/plain`**；请使用当前仓库中的 `**WeChatMiniProgramClient`**（字符串 + Jackson 解析），勿退回直接 `body(Class)`。                                                                                                                                                                                         |
| 带 Token 调接口仍 **code 500**（`message` 为「服务器内部错误」）           | 先用 `**POST /api/v1/auth/dev/login`**（`-Plocal-dev`）是否也失败：若同样失败，多为 MySQL 未启动 / 库 `home_accounting` 未建 / 账号密码与 `application.yml` 不一致。新版本会对数据库问题返回 code 503 及简要排查说明；dev profile 下 `message` 会附 SQL 异常详情。JWT / `LocalDateTime` 问题已用 `**UrlPathHelper`**、`**JavaTimeModule**` 处理；未加入家庭时应为 **403** 而非 500。 |


---

## 安全清单

- 生产环境关闭 `**dev`** profile；更换所有默认口令与 `**JWT_SECRET`**。
- **HTTPS**、最小防火墙暴露面、数据库与白名单或隧道。
- 密钥仅环境变量或密钥管理服务，**不进 Git**。


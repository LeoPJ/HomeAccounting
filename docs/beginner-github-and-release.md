# 小白版：代码已经推到 GitHub 之后要做什么

假设你已经用 Git / 桌面工具把项目推到了 GitHub 上。下面按顺序做即可；**不用一次做完**，可以停在任意一步，下次接着做。

---

## 第 0 步：确认 GitHub 上有没有自动在「跑检查」

1. 用浏览器打开你的 GitHub 仓库页面（地址一般是 `https://github.com/你的用户名/HomeAccounting` 之类）。
2. 点仓库顶部的 **「Actions」** 标签。
3. 看左侧是否出现 **「CI」** 工作流；点进去，看最近一次是不是在你 push 之后触发的。
4. 若**有绿色勾**：说明云端已自动执行 `mvn verify`，后端代码至少能编译通过。
5. 若**什么都没有**或从没跑过 CI：多半是当前分支不叫 `main` 或 `master`。在仓库页点 **「Code」** 看默认分支名字。若不是 `main`/`master`，要么以后都往 `main` 推代码，要么请会改配置的人改一下仓库里的 `.github/workflows/ci.yml` 里 `branches:` 那一行，加上你的分支名。

这一步**不需要在你自己电脑上敲命令**，全是网页里点。

---

## 第 1 步：准备一台云服务器（你已有机器就跳过购买）

你需要一台能 SSH 登录的 Linux（常见是 Ubuntu）。下面默认你已经能：

- 用 **IP 或域名** 登录；
- 有 **sudo 权限**。

在你**自己电脑**上打开终端（Windows 可用 PowerShell 或「终端」），测试登录（把下面示例改成你的 IP 和用户）：

```bash
ssh 你的用户名@你的服务器IP
```

能登录再进行下一步。

---

## 第 2 步：在服务器上安装 Java 和 MySQL（在 SSH 里执行）

以下命令都在 **已经 ssh 登录到服务器之后** 的终端里执行（一行一行复制即可；若系统不是 Ubuntu，包名可能略有不同）。

**安装 Java 17：**

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless
java -version
```

最后一行应能看到 `17` 字样。

**安装 MySQL（若已安装可跳过）：**

```bash
sudo apt install -y mysql-server
sudo mysql
```

进入 MySQL 提示符后，执行（把密码改成你自己要的）：

```sql
CREATE DATABASE IF NOT EXISTS home_accounting CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'homeaccounting'@'localhost' IDENTIFIED BY '这里改成强密码';
GRANT ALL PRIVILEGES ON home_accounting.* TO 'homeaccounting'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

记下：**数据库名** `home_accounting`、**用户名** `homeaccounting`、**密码**。

---

## 第 3 步：在服务器上放配置文件（仍在 SSH 里）

1. 在你**自己电脑**上，用记事本或 VS Code 打开本仓库里的 `**deploy/home-accounting.env.example`**，另存思路是：在服务器上新建一个文件，内容按下面做。
2. 在服务器上：

```bash
sudo nano /etc/home-accounting.env
```

1. 把示例里的内容贴进去，**务必改这几项**：

- `DB_PASSWORD=` 改成上一步 MySQL 用户密码  
- `JWT_SECRET=` 改成一长串随机字符（随便敲键盘多敲一些也行）  
- `WECHAT_MINI_APP_ID=`、`WECHAT_MINI_APP_SECRET=` 填微信小程序后台里的值

1. 保存：`Ctrl+O` 回车，`Ctrl+X` 退出。
2. 限制权限（安全一点）：

```bash
sudo chmod 600 /etc/home-accounting.env
```

---

## 第 4 步：先手动跑起来 jar（确认后端能活）

在还没用 GitHub 自动部署之前，建议先证明「jar + 数据库」能跑。

**在你自己电脑上**（项目已 clone 的情况下），进入后端目录打包：

```bash
cd backend
mvn -B verify
```

打包成功后，jar 在：

`backend/target/home-accounting-server-0.1.0-SNAPSHOT.jar`（版本号若变了，以 `target` 里实际文件名为准）

用 **scp** 拷到服务器（示例）：

```bash
scp backend/target/home-accounting-server-0.1.0-SNAPSHOT.jar 你的用户名@你的服务器IP:/home/你的用户名/
```

再到 **服务器 SSH** 里：

```bash
cd /home/你的用户名
set -a && source /etc/home-accounting.env && set +a
java -jar home-accounting-server-0.1.0-SNAPSHOT.jar
```

若看到 Spring Boot 启动日志且没有立刻报错退出，另开终端用浏览器或 curl 测一下（若端口是 8080）：

```bash
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/
```

能返回一个 HTTP 状态码（例如 `401` 或 `404` 也说明进程在听端口）。**Ctrl+C** 可停掉前台进程。

若你本机没有安装 Maven / Java，可以暂时跳过「本机打包」，等第 6 步用 GitHub 构建后再把 jar 从 Actions 产物或 SCP 弄上去——但对小白来说，本机先打一次最容易理解。

---

## 第 5 步：用 systemd 常驻运行（可选但推荐）

把仓库里的 `**deploy/home-accounting.service.example`** 打开，按注释改好路径后，在服务器上：

```bash
sudo nano /etc/systemd/system/home-accounting.service
```

粘贴改好的内容，然后：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now home-accounting
sudo systemctl status home-accounting
```

`active (running)` 即正常。具体路径要和下面 GitHub 部署里拷贝 jar 的位置一致（见第 7 步里的 `DEPLOY_REMOTE_COMMAND`）。

---

## 第 6 步：给 GitHub 一把「只用来部署」的 SSH 钥匙

在你**自己电脑**上（不要在服务器上也可以），PowerShell 或终端执行：

```bash
ssh-keygen -t ed25519 -f ./github_deploy_key -N ""
```

会生成两个文件：`github_deploy_key`（私钥）、`github_deploy_key.pub`（公钥）。

1. 用记事本打开 `**github_deploy_key.pub**`，全选复制。
2. **SSH 登录服务器**，执行：

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
nano ~/.ssh/authorized_keys
```

把刚才复制的**公钥**粘到**新的一行**末尾，保存退出。

```bash
chmod 600 ~/.ssh/authorized_keys
```

1. 用记事本打开 `github_deploy_key`（没有 `.pub` 的那个），**整份复制**（含 `BEGIN` / `END`）。这份是**私钥**，不要发给任何人、不要贴到公开 issue。

---

## 第 7 步：在 GitHub 网页里填「密钥」（Secrets）

1. 打开 GitHub 上你的仓库。
2. 点 **「Settings」**（设置）。
3. 左侧找到 **「Secrets and variables」** → **「Actions」**。
4. 点 **「New repository secret」**，依次新建下面几个（名字必须**完全一致**）：


| Name（名称）                | Value（内容填什么）                                                            |
| ----------------------- | ----------------------------------------------------------------------- |
| `DEPLOY_HOST`           | 服务器 IP 或域名                                                              |
| `DEPLOY_USER`           | SSH 登录用户名                                                               |
| `DEPLOY_SSH_KEY`        | 上一步里**整个私钥**文本                                                          |
| `DEPLOY_REMOTE_DIR`     | 服务器上的一个**目录**，用来接收上传的 jar，例如 `/home/你的用户名/incoming`（需要事先 `mkdir -p` 建好） |
| `DEPLOY_REMOTE_COMMAND` | 上传完成后在服务器执行的**一整段 shell**（可多行），用来「把 jar 拷到真正运行目录 + 重启服务」。示例（路径请改成你的）：   |


`DEPLOY_REMOTE_COMMAND` 示例内容（复制后改路径）：

```bash
sudo install -m 644 /home/你的用户名/incoming/home-accounting-server.jar /opt/home-accounting/home-accounting-server.jar && sudo systemctl restart home-accounting
```

说明：`scp-action` 会把文件传到 `DEPLOY_REMOTE_DIR` 下，文件名是 `home-accounting-server.jar`，所以上面 `install` 的源路径应是 `incoming` 目录下的该文件名。

若 SSH 不是 22 端口：需要改仓库里 `.github/workflows/deploy-backend.yml` 里的 `port: 22`（两处），改成你的端口后再 push 一次。

---

## 第 8 步：在 GitHub 上点一下「部署」

1. 仓库页点 **「Actions」**。
2. 左侧选 **「Deploy backend」**。
3. 右侧点 **「Run workflow」** → 再点绿色 **「Run workflow」**。
4. 等它跑完；若失败，点进那条记录，看红色步骤的日志（多半是 Secret 写错、路径不对、或服务器没权限执行 `sudo`）。

### 卡在「Upload jar to server」很久不动，正常吗？

**不太正常。** 日志里若一直停在 `scp file to server.`，多半是 **GitHub 机房的电脑连不上你这台服务器的 SSH 端口**，不是 jar 太大。

按下面顺序自查（仍不需要改业务代码）：

1. `**DEPLOY_HOST` 必须是公网能访问的地址**
  填 `10.x`、`172.16.x`、`192.168.x` 这类**内网 IP**，GitHub Actions 永远连不上。要用云控制台里的**公网 IP**，或已解析到公网的域名。
2. **云厂商「安全组 / 防火墙」要放行 SSH**
  很多人只在自己电脑能 `ssh` 上去，是因为安全组写的是「只允许我家宽带 IP」。**GitHub 的 IP 段不在你家**，所以网页上部署会一直卡住或超时。  
   **处理**：在阿里云 / 腾讯云 / AWS 等控制台里，给这台机器的 **入站规则** 增加：**TCP 22**，来源先设为 `0.0.0.0/0`（仅依赖密钥登录、不要用弱密码）。部署跑通后再考虑收紧来源 IP 或改用自建 Runner。
3. **公钥要贴在「部署用的那个 Linux 用户」下面**
  你在 GitHub Secret 里填的 `DEPLOY_USER`（例如 `ubuntu`），公钥就必须在这个用户主目录里：
   `/home/ubuntu/.ssh/authorized_keys`（示例）
   若密钥是在服务器上**用 root 生成**的，却把公钥只加进了 **root** 的 `authorized_keys`，而 `DEPLOY_USER` 填的是 **ubuntu**，就会认证失败（有时表现为长时间重试或超时）。  
   **正确关系**：谁登录，就把公钥放进**谁**的 `authorized_keys`；**私钥**整段放进 GitHub 的 `DEPLOY_SSH_KEY`（在服务器上生成密钥也可以，只要私钥复制到 GitHub、公钥复制到对应用户的 `authorized_keys` 即可）。
4. **在你自己电脑上测「外网是否通」**（把手机开热点，让电脑走热点，模拟「不在你家宽带」）：
  ```bash
   ssh -i 你的私钥文件路径 -o ConnectTimeout=10 -o BatchMode=yes DEPLOY_USER@DEPLOY_HOST "echo ok"
  ```
   若这里都连不上，先把安全组 / `DEPLOY_HOST` 修好，再重跑 GitHub 的 Deploy。

仓库里的 `deploy-backend.yml` 使用 Runner 自带的 **`scp` / `ssh`（OpenSSH）** 上传并执行远程命令，并带有 **ConnectTimeout** 与 **整步 `timeout-minutes`**；整次部署作业最多约 **25 分钟** 会被 GitHub 强制结束，避免像旧版 `appleboy/scp-action` 那样在 Docker 里无限卡住、也不吃你填的 `timeout` 参数。

修好网络后若仍失败，看该步红色日志里的英文报错（`Connection timed out`、`Permission denied` 等）再对症改。

若日志里已经出现 **`Permanently added ... to the list of known hosts`**（说明 TCP + SSH 认证都过了），却仍然卡在 **`scp`**：多半是客户端默认走 **SFTP 传文件**，而你服务器上的 SFTP 子系统或权限在传数据阶段挂住。当前仓库的 workflow 已改为用 **`ssh` + 标准输入写文件**（不经 `scp`/SFTP），并加 **`timeout 300`** 防止整步无限挂；请拉最新 `deploy-backend.yml` 再跑。

---

## 第 9 步：小程序连上你的服务器

1. 服务器前面通常还要 **Nginx + HTTPS**（微信小程序要求 **https**）。这一步若你还没做，需要按云厂商文档装证书、反代到 `127.0.0.1:8080`。做完后你会得到一个类似 `https://api.你的域名.com` 的地址。
2. 在本项目里打开 `frontend/config.js`，把 `API_BASE` 改成上面的 **https 根地址**（不要尾斜杠一般即可）。
3. 打开 **微信公众平台** → 你的小程序 → **开发** → **开发管理** → **开发设置** → **服务器域名** → 在 **request 合法域名** 里填**同一个域名**（只填域名，不要路径）。
4. 用 **微信开发者工具** 打开 `frontend` 目录，真机预览测登录、记账等。

---

## 你现在已经 push 了，最小「下一步」总结

只做三件事也不会错：

1. **GitHub → Actions**：看 **CI** 有没有绿勾。
2. **服务器**：装好 Java 17 + MySQL，建好库和用户，写好 `/etc/home-accounting.env`。
3. **能连上 API 的 https 之后**：改 `frontend/config.js`，微信后台配域名。

GitHub 自动部署（第 6～8 步）可以等你在第 4 步手动 jar 跑通后再做。

更偏「运维清单」的细节仍见：**[production-and-release.md](./production-and-release.md)**。
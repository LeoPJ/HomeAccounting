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

1. 在你**自己电脑**上，用记事本或 VS Code 打开本仓库里的 `deploy/home-accounting.env.example`，按示例在服务器上新建配置文件。
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

若你本机没有安装 Maven / Java，可以暂时跳过「本机打包」，等 **第 6 步** 在 GitHub 上跑 **Build jar** 再在服务器用脚本或浏览器下载工件。

---

## 第 5 步：用 systemd 常驻运行（可选但推荐）

把仓库里的 `deploy/home-accounting.service.example` 打开，按注释改好路径后，在服务器上：

```bash
sudo nano /etc/systemd/system/home-accounting.service
```

粘贴改好的内容，然后：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now home-accounting
sudo systemctl status home-accounting
```

`active (running)` 即正常。`ExecStart` 里的 jar 路径要和 **第 8 步** 脚本安装的目录一致（默认可用 `/opt/home-accounting/home-accounting-server.jar`）。

---

## 第 6 步：在 GitHub 上打出「可下载的 jar」（不上传你的服务器）

1. 浏览器打开你的仓库 → 点 **「Actions」**。
2. 左侧选 **「Build jar」**（若没有，先把你本地包含 `build-jar.yml` 的提交 **push** 到 GitHub）。
3. 右侧点 **「Run workflow」** → 分支选 **`main`**（或你实际用的默认分支）→ 点绿色 **「Run workflow」**。
4. 等出现**绿色勾**。点进这次运行，页面底部 **Artifacts** 里会有 **`home-accounting-jar`**（一个 zip，解压后就是 `home-accounting-server.jar`）。你也可以不下载，交给下一步脚本自动拉。

---

## 第 7 步：给服务器一把「只读你仓库 Actions」的 GitHub 令牌

在 **GitHub 网页**（不是服务器）操作：

1. 右上角头像 → **Settings** → 左侧最底部 **Developer settings** → **Personal access tokens**。
2. 新建 **Fine-grained** 或 **Classic** 令牌均可：
   - **Classic**：私有仓库勾选 **`repo`** 即可覆盖拉取工件所需权限（最简单）。
   - **Fine-grained**：选你的仓库，权限里打开 **Actions: Read**、**Metadata: Read**（只读）。
3. 生成后**复制令牌字符串**（只显示一次）。不要发到聊天、不要写进 Git 仓库。

---

## 第 8 步：在服务器上用脚本拉 jar 并重启（每次要部署时执行）

**SSH 登录服务器**，安装依赖（Ubuntu 示例）：

```bash
sudo apt update
sudo apt install -y curl jq unzip
```

把仓库里的 `deploy/fetch-jar-from-github.sh` 拷到服务器（或用 `git clone` 整个项目后进入目录），例如：

```bash
sudo cp /path/to/HomeAccounting/deploy/fetch-jar-from-github.sh /usr/local/bin/home-accounting-fetch-jar
sudo chmod +x /usr/local/bin/home-accounting-fetch-jar
```

复制环境文件并编辑（把 `GITHUB_TOKEN`、`GITHUB_OWNER`、`GITHUB_REPO`、`GITHUB_BRANCH` 改成你的；`INSTALL_DIR` 与 systemd 里 jar 路径一致）：

```bash
sudo cp /path/to/HomeAccounting/deploy/github-fetch.env.example /etc/home-accounting/github-fetch.env
sudo nano /etc/home-accounting/github-fetch.env
sudo chmod 600 /etc/home-accounting/github-fetch.env
```

**每次要上线后端时**：先在 GitHub 跑通一次 **Build jar**（第 6 步），再在服务器执行（脚本会默认 `source /etc/home-accounting/github-fetch.env`）：

```bash
sudo /usr/local/bin/home-accounting-fetch-jar
```

若你把 env 放在别的路径：`sudo ENV_FILE=/path/to/xxx.env /usr/local/bin/home-accounting-fetch-jar`。

**不想用脚本时**：也可以在 **Build jar** 成功那次运行页里 **手动下载** `home-accounting-jar.zip`，解压出 jar 后，用 `scp` 或 `sudo cp` 放到 `INSTALL_DIR`，再 `sudo systemctl restart home-accounting`。

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

后端发版流程：**push 代码 → CI 绿勾 →（需要发 jar 时）手动 Run「Build jar」→ 服务器执行拉包脚本或手工下载工件**。

更偏「运维清单」的细节仍见：**[production-and-release.md](./production-and-release.md)**。
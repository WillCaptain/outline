# Outline Playground — 阿里云部署指南

> **傻瓜式部署**：按顺序执行下面的步骤即可，不需要任何 DevOps 经验。

---

## 目录结构

```
deploy/
├── outline-playground.jar   ← Spring Boot 主程序（Fat-JAR）
├── application.properties   ← 配置文件（端口、数据库路径、阿里云SMS）
├── install.sh               ← 一键安装脚本（本文档的核心）
├── start.sh                 ← 手动启动（本地测试用）
├── stop.sh                  ← 手动停止（本地测试用）
└── DEPLOY.md                ← 本文件
```

---

## 第一步：购买并配置阿里云 ECS

1. 打开 [阿里云 ECS 控制台](https://ecs.console.aliyun.com)
2. 点击 **创建实例**，推荐配置：

   | 参数 | 推荐值 |
   |------|--------|
   | 地域 | 选离用户最近的 |
   | 实例规格 | 2核2G 起步（ecs.e-c1m2.large 或以上） |
   | 镜像 | **Ubuntu 22.04 LTS**（推荐）或 Alibaba Cloud Linux 3 |
   | 系统盘 | 40 GB 高效云盘 |
   | 公网IP | 勾选"分配公网IP"，带宽 1 Mbps 起步 |

3. **安全组**（关键！）点击"安全组"→ 添加入方向规则：

   | 授权策略 | 协议 | 端口 | 授权对象 |
   |---------|------|------|---------|
   | 允许 | TCP | **80** | 0.0.0.0/0 |
   | 允许 | TCP | **443** | 0.0.0.0/0 |
   | 允许 | TCP | **8080** | 0.0.0.0/0 |
   | 允许 | TCP | **22** | 你的本机IP（SSH管理） |

4. 记录服务器的**公网 IP 地址**（后面会用到）。

---

## 第二步：上传部署包到服务器

### 方式 A：scp 命令（Mac / Linux 本机）

```bash
# 将整个 deploy/ 目录上传到服务器的 /root/deploy/
scp -r /path/to/outline/deploy root@<服务器IP>:/root/
```

### 方式 B：阿里云控制台文件上传

在 ECS 控制台点击 **远程连接** → **Cloud Shell** → 上传文件。

### 方式 C：通过 Git 拉取

```bash
# 先 SSH 登录服务器（见第三步），然后执行：
git clone https://github.com/twelve-cloud/outline.git
cd outline/deploy
```

---

## 第三步：SSH 登录服务器

```bash
ssh root@<服务器IP>
# 输入购买时设置的密码，或使用密钥登录
```

---

## 第四步：一键安装

> 以下命令在**服务器**上执行。

```bash
cd /root/deploy

# 方案A：不带域名，直接用 IP 访问（最简单）
sudo bash install.sh

# 方案B：安装 Nginx 反向代理（推荐，可后续配 HTTPS）
sudo bash install.sh --nginx

# 方案C：安装 Nginx + 绑定域名
sudo bash install.sh --nginx --domain your.domain.com
```

脚本会自动：
- ✅ 安装 Java 21
- ✅ 创建 `/opt/outline-playground/` 目录及专用系统用户
- ✅ 注册 systemd 服务（开机自动启动）
- ✅ 开放防火墙端口
- ✅ 安装并配置 Nginx（如果选了 --nginx）
- ✅ 启动服务

安装完成后你会看到：

```
╔══════════════════════════════════════════════════════════╗
║          Outline Playground 安装完成！                  ║
╚══════════════════════════════════════════════════════════╝

  访问地址：http://<服务器IP>:8080
```

打开浏览器访问即可！

---

## 第五步（可选）：配置阿里云短信

> 不配置也能用，验证码会显示在登录弹窗里（开发模式）。

### 5.1 获取 AccessKey

1. 打开 [RAM 控制台](https://ram.console.aliyun.com) → **用户** → 新建子用户
2. 勾选"编程访问"，记录 **AccessKey ID** 和 **AccessKey Secret**
3. 给该用户赋予 **AliyunDysmsFullAccess** 权限

### 5.2 申请短信签名和模板

1. 打开 [短信服务控制台](https://dysms.console.aliyun.com)
2. 签名管理 → 申请签名（审核约 1 个工作日）
3. 模板管理 → 申请模板，内容示例：
   ```
   您的验证码为 ${code}，5分钟内有效，请勿泄露给他人。
   ```

### 5.3 填写配置

```bash
sudo nano /opt/outline-playground/application.properties
```

修改以下几行：

```properties
aliyun.sms.enabled=true
aliyun.sms.access-key-id=你的AccessKeyID
aliyun.sms.access-key-secret=你的AccessKeySecret
aliyun.sms.sign-name=你的签名名称
aliyun.sms.template-code=SMS_xxxxxxxxx
```

保存后重启：

```bash
sudo systemctl restart outline-playground
```

---

## 第六步（可选）：配置 HTTPS 域名

> 需要先将域名解析到服务器 IP，并完成第四步的 `--nginx` 安装。

```bash
# 安装 certbot
sudo apt-get install -y certbot python3-certbot-nginx

# 一键申请并配置 SSL 证书（免费，自动续期）
sudo certbot --nginx -d your.domain.com

# 测试自动续期
sudo certbot renew --dry-run
```

---

## 常用运维命令

```bash
# 查看服务状态
sudo systemctl status outline-playground

# 实时查看应用日志
sudo tail -f /var/log/outline-playground/app.log

# 重启服务
sudo systemctl restart outline-playground

# 停止服务
sudo systemctl stop outline-playground

# 查看错误日志
sudo tail -f /var/log/outline-playground/error.log

# 查看 systemd 日志
sudo journalctl -u outline-playground -n 100 -f
```

---

## 升级应用（更新 JAR）

每次发布新版本后：

```bash
# 1. 将新 JAR 上传到服务器
scp outline-playground.jar root@<服务器IP>:/root/

# 2. 在服务器上替换 JAR 并重启
sudo cp /root/outline-playground.jar /opt/outline-playground/
sudo systemctl restart outline-playground

# 3. 确认服务正常
sudo systemctl status outline-playground
```

---

## 常见问题排查

**Q：安装后浏览器访问不了？**

1. 检查阿里云安全组是否放行了 80 或 8080 端口（最常见原因）
2. 检查服务是否在运行：`sudo systemctl status outline-playground`
3. 检查端口是否监听：`sudo ss -tlnp | grep 8080`

**Q：首次访问很慢（10 秒以上）？**

正常现象。首次请求会触发语法文件编译，之后所有请求秒级响应。

**Q：服务崩溃后不会自动恢复？**

systemd 已配置 `Restart=on-failure`，会自动重启。查看原因：
```bash
sudo journalctl -u outline-playground -n 50 --no-pager
```

**Q：数据库在哪里？如何备份？**

```bash
# 数据库路径
ls -lh /opt/outline-playground/data/playground.db

# 备份（停机或热备均可，SQLite 文件直接拷贝即可）
sudo cp /opt/outline-playground/data/playground.db ~/playground-backup-$(date +%Y%m%d).db
```

**Q：如何修改端口？**

```bash
sudo nano /opt/outline-playground/application.properties
# 修改 server.port=9090
sudo systemctl restart outline-playground
# 记得同时更新阿里云安全组放行新端口
```

---

## 目录结构（服务器）

安装完成后服务器上的布局：

```
/opt/outline-playground/
├── outline-playground.jar   ← 程序主体
├── application.properties   ← 配置文件
└── data/
    └── playground.db        ← SQLite 数据库（用户/片段/评论）

/var/log/outline-playground/
├── app.log                  ← 应用日志
└── error.log                ← 错误日志
```

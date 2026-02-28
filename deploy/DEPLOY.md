# Outline Playground — 部署指南

## 目录结构

运维人员收到的 `deploy/` 目录包含以下文件：

```
deploy/
├── outline-playground.jar   ← Spring Boot Fat-JAR（主程序）
├── application.properties   ← 配置文件（可按需修改）
├── start.sh                 ← 启动脚本
├── stop.sh                  ← 停止脚本（daemon 模式）
└── DEPLOY.md                ← 本文件
```

---

## 环境要求

| 组件 | 最低版本 |
|------|---------|
| Java | 21 (LTS) |
| 操作系统 | Linux / macOS / Windows (WSL) |
| 内存 | 512 MB 可用 |
| 网络端口 | 8080（可在配置文件中修改） |

```bash
# 验证 Java 版本
java -version
# 应显示: openjdk version "21.x.x" ...
```

---

## 快速部署（前台运行）

```bash
cd deploy/
chmod +x start.sh stop.sh
./start.sh
```

访问 `http://<服务器IP>:8080` 即可打开 Playground。

按 `Ctrl+C` 停止。

---

## 后台（Daemon）部署

```bash
cd deploy/
chmod +x start.sh stop.sh

# 启动（日志写入 playground.log）
./start.sh --daemon

# 查看日志
tail -f playground.log

# 停止
./stop.sh
```

---

## 配置说明

编辑 `application.properties` 按需调整：

```properties
# 监听端口（默认 8080）
server.port=8080

# 绑定特定网卡（默认监听所有接口）
# server.address=127.0.0.1

# JVM 堆大小（通过环境变量覆盖）
# export JVM_OPTS="-Xmx1g -Xms256m"
```

---

## 反向代理（Nginx 示例）

如需通过域名访问，配置 Nginx：

```nginx
server {
    listen 80;
    server_name playground.example.com;

    location / {
        proxy_pass         http://127.0.0.1:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
    }
}
```

HTTPS（Let's Encrypt）：

```bash
certbot --nginx -d playground.example.com
```

---

## Systemd 服务（可选）

创建 `/etc/systemd/system/outline-playground.service`：

```ini
[Unit]
Description=Outline Playground
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/outline-playground
ExecStart=/usr/bin/java -Xmx512m -jar /opt/outline-playground/outline-playground.jar \
    --spring.config.location=file:/opt/outline-playground/application.properties
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

```bash
# 将 deploy/ 内容拷贝到 /opt/outline-playground/
sudo cp -r deploy/* /opt/outline-playground/

sudo systemctl daemon-reload
sudo systemctl enable outline-playground
sudo systemctl start outline-playground
sudo systemctl status outline-playground
```

---

## 构建 JAR（开发者）

```bash
# 在项目根目录执行（首次需要联网下载依赖）
cd /path/to/outline

# 1. 构建 outline 模块（GCP 引擎）
cd outline && mvn install -DskipTests && cd ..

# 2. 构建 playground Fat-JAR
cd playground && mvn package -DskipTests

# 3. 将 JAR 拷贝到 deploy/ 目录
cp target/outline-playground-*.jar ../deploy/outline-playground.jar
```

---

## 常见问题

**Q: 启动后 `http://localhost:8080` 无法访问？**  
A: 检查防火墙是否放行端口：
```bash
# Ubuntu/Debian
sudo ufw allow 8080/tcp

# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=8080/tcp && sudo firewall-cmd --reload
```

**Q: 首次请求很慢（5-10 秒）？**  
A: 正常现象。首次请求会触发 MSLL 语法文件的编译，之后所有请求都会很快。

**Q: 如何修改示例列表？**  
A: 通过 Playground 界面右下角 "＋ Contribute Example" 在线添加，或直接 POST `/api/examples`。

**Q: 如何清理 share 链接数据？**  
A: Share tokens 存储在内存中，重启后自动清空。如需持久化，配置 `share.storage.dir`。

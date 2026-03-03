#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
#  Outline Playground — 一键安装脚本
#  适用系统：Ubuntu 20.04/22.04/24.04 · CentOS 7/8 · Alibaba Cloud Linux 2/3
#
#  用法（以 root 或 sudo 身份执行）：
#    sudo bash install.sh              # 基础安装，监听 8080 端口
#    sudo bash install.sh --nginx      # 同时安装 Nginx 反向代理（推荐）
#    sudo bash install.sh --port 9090  # 自定义端口
#    sudo bash install.sh --nginx --domain your.domain.com  # 带域名
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

# ── 颜色输出 ──────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
info()    { echo -e "${BLUE}[INFO]${NC}  $*"; }
ok()      { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
section() { echo -e "\n${BLUE}══ $* ══${NC}"; }

# ── 参数解析 ──────────────────────────────────────────────────────────────
INSTALL_NGINX=false
APP_PORT=8080
DOMAIN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --nginx)  INSTALL_NGINX=true; shift ;;
    --port)   APP_PORT="$2"; shift 2 ;;
    --domain) DOMAIN="$2"; shift 2 ;;
    *) warn "未知参数: $1"; shift ;;
  esac
done

# ── 必须以 root 运行 ────────────────────────────────────────────────────
[[ $EUID -ne 0 ]] && error "请用 sudo 执行：sudo bash install.sh"

# ── 检测脚本所在目录（即 deploy 包的位置）──────────────────────────────
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_SRC="$DEPLOY_DIR/outline-playground.jar"
[[ -f "$JAR_SRC" ]] || error "未找到 outline-playground.jar，请确保在 deploy/ 目录下执行此脚本"

# ── 常量 ────────────────────────────────────────────────────────────────
APP_DIR="/opt/outline-playground"
DATA_DIR="$APP_DIR/data"
LOG_DIR="/var/log/outline-playground"
APP_USER="outline"
SERVICE_NAME="outline-playground"

# ════════════════════════════════════════════════════════════════════════════
section "1 / 5  检测操作系统"
# ════════════════════════════════════════════════════════════════════════════
if   [[ -f /etc/os-release ]]; then
  . /etc/os-release
  OS_ID="${ID:-unknown}"
  OS_VERSION="${VERSION_ID:-0}"
  info "系统: $PRETTY_NAME"
elif [[ "$(uname -s)" == "Darwin" ]]; then
  OS_ID="macos"
  info "系统: macOS（仅用于本地测试，建议在 Linux 服务器上运行此脚本）"
else
  OS_ID="unknown"
  warn "无法识别操作系统，将尝试继续安装"
fi

# 包管理器判断
if   command -v apt-get &>/dev/null; then PKG="apt";
elif command -v yum     &>/dev/null; then PKG="yum";
elif command -v dnf     &>/dev/null; then PKG="dnf";
else warn "未找到包管理器，跳过系统包安装"; PKG="none"; fi

ok "包管理器: ${PKG}"

# ════════════════════════════════════════════════════════════════════════════
section "2 / 5  安装 Java 21"
# ════════════════════════════════════════════════════════════════════════════
install_java() {
  info "正在安装 OpenJDK 21 …"
  case "$PKG" in
    apt)
      apt-get update -qq
      # Ubuntu 24.04+ 仓库自带 openjdk-21；旧版本需加 PPA
      if apt-cache show openjdk-21-jdk &>/dev/null 2>&1; then
        apt-get install -y -qq openjdk-21-jdk
      else
        apt-get install -y -qq software-properties-common
        add-apt-repository -y ppa:openjdk-r/ppa
        apt-get update -qq
        apt-get install -y -qq openjdk-21-jdk
      fi
      ;;
    yum|dnf)
      # 阿里云 Linux 2/3 和 CentOS 通过 yum/dnf 安装
      $PKG install -y java-21-openjdk-headless 2>/dev/null || {
        # 若系统仓库没有 21，从 Adoptium 手动下载
        info "系统仓库无 JDK 21，从 Adoptium 下载 …"
        ARCH=$(uname -m)
        [[ "$ARCH" == "aarch64" ]] && JDK_ARCH="aarch64" || JDK_ARCH="x64"
        JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.4%2B7/OpenJDK21U-jdk_${JDK_ARCH}_linux_hotspot_21.0.4_7.tar.gz"
        TMP_DIR=$(mktemp -d)
        curl -fsSL "$JDK_URL" -o "$TMP_DIR/jdk21.tar.gz"
        tar -xzf "$TMP_DIR/jdk21.tar.gz" -C "$TMP_DIR"
        JDK_HOME="$TMP_DIR/$(ls "$TMP_DIR" | grep jdk-21)"
        mkdir -p /usr/lib/jvm
        mv "$JDK_HOME" /usr/lib/jvm/jdk-21
        update-alternatives --install /usr/bin/java java /usr/lib/jvm/jdk-21/bin/java 211
        rm -rf "$TMP_DIR"
      }
      ;;
    none)
      warn "跳过 Java 安装，请确保 java -version 显示 21.x.x"
      return
      ;;
  esac
}

if java -version 2>&1 | grep -qE '"(21|22|23|24)'; then
  JAVA_VER=$(java -version 2>&1 | head -1)
  ok "Java 已安装: $JAVA_VER"
else
  install_java
  java -version 2>&1 | grep -qE '"(21|22|23|24)' || error "Java 21 安装失败，请手动安装"
  ok "Java 21 安装完成"
fi

# ════════════════════════════════════════════════════════════════════════════
section "3 / 5  部署应用文件"
# ════════════════════════════════════════════════════════════════════════════

# 创建专用系统用户（无 shell 登录权限）
if ! id "$APP_USER" &>/dev/null; then
  useradd --system --no-create-home --shell /usr/sbin/nologin "$APP_USER"
  ok "创建系统用户: $APP_USER"
fi

# 创建目录
mkdir -p "$APP_DIR" "$DATA_DIR" "$LOG_DIR"
chown -R "$APP_USER:$APP_USER" "$APP_DIR" "$LOG_DIR"
chmod 750 "$APP_DIR" "$DATA_DIR"

# 复制 JAR
cp -f "$JAR_SRC" "$APP_DIR/outline-playground.jar"
ok "JAR 已复制 → $APP_DIR/outline-playground.jar"

# 复制 application.properties（若已存在则不覆盖，保留用户自定义配置）
if [[ ! -f "$APP_DIR/application.properties" ]]; then
  cp "$DEPLOY_DIR/application.properties" "$APP_DIR/application.properties"
  # 更新端口配置
  sed -i "s/^server\.port=.*/server.port=$APP_PORT/" "$APP_DIR/application.properties"
  ok "配置文件已复制 → $APP_DIR/application.properties"
else
  # 仅更新 JAR，保留现有配置
  warn "已存在配置文件，跳过覆盖（若要重置：sudo cp $DEPLOY_DIR/application.properties $APP_DIR/）"
fi

chown "$APP_USER:$APP_USER" "$APP_DIR/application.properties"

# ════════════════════════════════════════════════════════════════════════════
section "4 / 5  配置 systemd 服务"
# ════════════════════════════════════════════════════════════════════════════

cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<EOF
[Unit]
Description=Outline Playground
Documentation=https://github.com/twelve-cloud/outline
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${APP_DIR}

# JVM 参数（可按需调整 -Xmx）
Environment="JAVA_OPTS=-Xmx512m -Xms128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

ExecStart=/usr/bin/java \${JAVA_OPTS} \\
    -jar ${APP_DIR}/outline-playground.jar \\
    --spring.config.location=file:${APP_DIR}/application.properties

# 重启策略：崩溃后 5 秒自动重启，最多 5 次
Restart=on-failure
RestartSec=5
StartLimitInterval=60s
StartLimitBurst=5

# 日志
StandardOutput=append:${LOG_DIR}/app.log
StandardError=append:${LOG_DIR}/error.log

# 安全加固
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=${APP_DIR}/data ${LOG_DIR}
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
ok "systemd 服务已注册: $SERVICE_NAME"

# ════════════════════════════════════════════════════════════════════════════
section "4.5  配置防火墙"
# ════════════════════════════════════════════════════════════════════════════
open_port() {
  local port=$1
  if command -v ufw &>/dev/null && ufw status | grep -q "active"; then
    ufw allow "$port/tcp" && ok "ufw: 已开放端口 $port"
  fi
  if command -v firewall-cmd &>/dev/null && firewall-cmd --state 2>/dev/null | grep -q running; then
    firewall-cmd --permanent --add-port="$port/tcp" && firewall-cmd --reload && ok "firewalld: 已开放端口 $port"
  fi
}
open_port "$APP_PORT"
[[ "$INSTALL_NGINX" == true ]] && open_port 80 && open_port 443

# ════════════════════════════════════════════════════════════════════════════
section "5 / 5  安装 Nginx 反向代理"
# ════════════════════════════════════════════════════════════════════════════
if [[ "$INSTALL_NGINX" == true ]]; then
  if ! command -v nginx &>/dev/null; then
    info "正在安装 Nginx …"
    case "$PKG" in
      apt)      apt-get install -y -qq nginx ;;
      yum|dnf)  $PKG install -y nginx && systemctl enable nginx ;;
    esac
    ok "Nginx 安装完成"
  else
    ok "Nginx 已安装"
  fi

  # 生成 Nginx 配置
  NGINX_CONF="/etc/nginx/conf.d/${SERVICE_NAME}.conf"
  SERVER_NAME="${DOMAIN:-_}"

  cat > "$NGINX_CONF" <<EOF
# Outline Playground — Nginx 配置
# 生成时间: $(date)
upstream outline_backend {
    server 127.0.0.1:${APP_PORT};
    keepalive 32;
}

server {
    listen 80;
    server_name ${SERVER_NAME};

    # 安全头
    add_header X-Frame-Options SAMEORIGIN;
    add_header X-Content-Type-Options nosniff;

    # 上传限制（用于 Contribute Example）
    client_max_body_size 2m;

    # 静态资源缓存
    location ~* \.(js|css|png|ico|woff2)$ {
        proxy_pass         http://outline_backend;
        proxy_set_header   Host \$host;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    location / {
        proxy_pass         http://outline_backend;
        proxy_set_header   Host              \$host;
        proxy_set_header   X-Real-IP         \$remote_addr;
        proxy_set_header   X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;

        # SSE / 长连接支持
        proxy_buffering off;
        proxy_cache     off;
    }
}
EOF

  # 测试 Nginx 配置
  nginx -t && systemctl restart nginx && ok "Nginx 配置已应用"

  [[ -n "$DOMAIN" ]] && {
    info "提示：若需 HTTPS，安装 certbot 后执行："
    echo "      sudo certbot --nginx -d $DOMAIN"
  }
else
  warn "未安装 Nginx。如需反向代理，重新执行：sudo bash install.sh --nginx [--domain your.domain.com]"
fi

# ════════════════════════════════════════════════════════════════════════════
#  启动服务
# ════════════════════════════════════════════════════════════════════════════
section "启动服务"
systemctl restart "$SERVICE_NAME"
sleep 3

if systemctl is-active --quiet "$SERVICE_NAME"; then
  ok "服务已启动 ✓"
else
  error "服务启动失败！请查看日志: sudo journalctl -u $SERVICE_NAME -n 50"
fi

# ════════════════════════════════════════════════════════════════════════════
#  安装完成摘要
# ════════════════════════════════════════════════════════════════════════════
PUBLIC_IP=$(curl -s --connect-timeout 3 http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null \
         || curl -s --connect-timeout 3 http://100.100.100.200/latest/meta-data/eipaddress 2>/dev/null \
         || echo "<服务器公网IP>")

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗"
echo -e "║          Outline Playground 安装完成！                  ║"
echo -e "╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
if [[ "$INSTALL_NGINX" == true ]]; then
  [[ -n "$DOMAIN" ]] && echo -e "  访问地址：${GREEN}http://$DOMAIN${NC}"
  echo -e "  访问地址：${GREEN}http://$PUBLIC_IP${NC}"
else
  echo -e "  访问地址：${GREEN}http://$PUBLIC_IP:$APP_PORT${NC}"
fi
echo ""
echo -e "  ⚠️  ${YELLOW}阿里云安全组${NC}：请在控制台开放端口 $APP_PORT（或 80/443）"
echo ""
echo "  常用命令："
echo "    查看状态:  sudo systemctl status $SERVICE_NAME"
echo "    查看日志:  sudo tail -f $LOG_DIR/app.log"
echo "    重启服务:  sudo systemctl restart $SERVICE_NAME"
echo "    停止服务:  sudo systemctl stop $SERVICE_NAME"
echo ""
echo "  配置文件:  $APP_DIR/application.properties"
echo "  数据库:    $APP_DIR/data/playground.db"
echo ""
echo -e "  ${YELLOW}下一步：编辑配置文件，填入阿里云短信 AccessKey${NC}"
echo "    sudo nano $APP_DIR/application.properties"
echo "    sudo systemctl restart $SERVICE_NAME"

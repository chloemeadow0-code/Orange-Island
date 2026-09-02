# 橘子岛官网

这是一个纯静态网站（无后端、无数据库），包含官网首页与完整使用规则页面。

---

## 文件结构

```
website/
├── index.html              # 官网首页
├── rules.html              # 完整使用规则（阅读页）
├── Dockerfile              # Zeabur Docker 部署配置
├── nginx.conf              # nginx 静态站配置
├── zbpack.json             # 强制 Zeabur 识别为静态网站
├── zeabur.json             # 同上，兼容不同版本
├── README.md               # 本文档
└── assets/
    ├── config.js           # 站点配置（应用名 / GitHub 仓库地址）
    └── style.css           # 样式
```

---

## 部署到 Zeabur

### 方式 A：直接上传 website 内文件

1. 把 `website/` 目录下的所有文件打包成 zip（包括 `index.html`、`assets/`、`Dockerfile`、`nginx.conf` 等）。
2. 登录 [Zeabur](https://zeabur.com)。
3. 创建新项目 → 选择 **Upload your source code**。
4. 上传 zip。
5. Zeabur 看到 `Dockerfile` 会自动用 Docker 部署，不会再误判成 Node.js。
6. Build Command 留空，Start Command 留空。
7. 绑定域名，部署。

> 如果访问域名显示 502，说明 Docker 容器起来了但 Zeabur 连不上 nginx。检查：
> - Service 日志里 nginx 是否正常启动
> - 是否能看到 `HEALTHCHECK` 通过
> - 端口是否暴露 80（Dockerfile 里写了 `EXPOSE 80`）

### 方式 B：GitHub 连接

1. 创建新项目 → 选择 **Deploy your source code**。
2. 授权 GitHub，选择 `chloemeadow0-code/Orange-Island` 仓库。
3. 在 Service 设置里把 **Root Directory** 改为 `website`。
4. 不要填 Build Command。
5. 绑定域名，部署。

> 如果仍然报 `Cannot find module '/src/index.js'`，说明 Zeabur 还是识别成了 Node.js。**请删除当前 Service，重新创建时手动选择 Static Site**。

---

## 本地预览

```bash
cd website
python -m http.server 8080
```

然后打开 http://localhost:8080。

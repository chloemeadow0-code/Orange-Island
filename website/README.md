# 橘子岛官网 / 审核答题系统

这是一个纯静态网站，配合 Supabase 实现审核答题、去重、后台看板功能。

---

## 文件结构

```
website/
├── index.html              # 官网首页
├── rules.html              # 完整使用规则（阅读页）
├── quiz.html               # 审核答题入口
├── admin.html              # 提交记录后台
├── Dockerfile              # Zeabur Docker 部署配置
├── nginx.conf              # nginx 静态站配置
├── zbpack.json             # 强制 Zeabur 识别为静态网站
├── zeabur.json             # 同上，兼容不同版本
├── README.md               # 本文档
└── assets/
    ├── config.js           # Supabase 等配置
    ├── supabase.min.js     # Supabase JS 库（本地，不依赖 CDN）
    ├── style.css           # 样式
    ├── supabase-client.js  # Supabase 封装
    └── quiz.js             # 答题逻辑
```

---

## 第一步：配置 Supabase

1. 登录 [Supabase Studio](https://supabase.com/dashboard)。
2. 进入 SQL Editor → New query。
3. 打开 `scripts/supabase/quiz_schema.sql`（用 VS Code，不要从聊天复制），全选复制粘贴到 SQL Editor，点击 **Run**。
4. 打开 `scripts/supabase/quiz_seed.sql`，同样方式执行，导入初始 25 道题。

执行后会得到：
- `quiz_questions` 题库表
- `quiz_submissions` 提交记录表（`qq_number` 唯一约束）
- `admin_settings` 管理员 Token 表
- 5 个 RPC：`get_active_questions`、`check_qq_submitted`、`submit_quiz`、`get_submissions`、`get_submission_stats`

> 查看 admin token：进入 Table Editor → `admin_settings`，找到 `admin_token` 的值。访问 `你的域名/admin.html` 输入即可查看提交记录。

---

## 第二步：部署到 Zeabur

> `website/assets/config.js` 中已经填好 Supabase 凭证。`SUPABASE_ANON_KEY` 是公开凭证，写在前端是正常做法，无需担心泄露。

### 方式 A：直接上传 website 内文件（你正在用的方式）

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
2. 授权 GitHub，选择 `orangeisland/app` 仓库。
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

---

## 题库更新

### 方法 1：Supabase Studio 直接编辑（最方便）

1. 进入 Supabase Dashboard → Table Editor → `quiz_questions`。
2. 新增/修改/停用题目。
3. `is_active=false` 的题目不会出现在考试中。

### 方法 2：修改 SQL 文件

编辑 `scripts/supabase/quiz_seed.sql`，重新执行即可（使用 `on conflict (id) do update`，可重复执行）。

---

## 后台看板

访问 `https://你的域名/admin.html`，输入 `admin_settings` 表中的 `admin_token` 即可查看：

- 总提交数
- 通过/未通过人数
- 所有提交记录
- 导出 CSV

---

## 防作弊说明

| 机制 | 是否实现 | 说明 |
|---|---|---|
| 一个 QQ 只能提交一次 | ✅ | 数据库 unique 约束 + RPC 校验 |
| 题目随机、选项乱序 | ✅ | 服务端 `random()` + 前端 shuffle |
| 答案不在前端暴露 | ✅ | 只下发题目和选项 |
| 服务端判分 | ✅ | `submit_quiz` RPC 计算分数 |
| 全对通过 | ✅ | `passed = (score == total)` |
| 答题限时 | ✅ | 前端倒计时，超时自动提交 |
| 防截屏 | ❌ | 纯 H5 做不到，需原生 App 加 `FLAG_SECURE` |

---

## 本地预览

因为使用了 `file://` 协议时部分浏览器会拦截跨域请求，建议用本地服务器预览：

```bash
cd website
python -m http.server 8080
```

然后打开 http://localhost:8080。

---

## 故障排查

| 现象 | 原因/解决 |
|---|---|
| 答题页白屏或报错 | `config.js` 中的 Supabase 凭证未填写或填错 |
| 拉不到题目 | `quiz_questions` 表为空，或 RLS 策略未启用 anon select |
| 提交失败提示 already_submitted | 该 QQ 已提交过，或并发时 unique 约束拦截 |
| admin 看板无数据 | admin token 错误，或没有提交记录 |
| 部署后样式丢失 | 检查路径是否正确，Root Directory 是否设为 `website` |

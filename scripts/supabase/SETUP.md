# 橘子岛 — Supabase 后台配置步骤

注册/登录/邀请码功能依赖一个 Supabase 项目。按下面四步配置即可。

**重要**：本项目的登录**只看用户名 + 邀请码**，不收集用户真实邮箱，也不发验证邮件。Supabase Auth 后端仍走 email+password 协议，但用合成邮箱 `username@users.orangeisland.local`（用户不感知）。因此**必须关闭"Confirm email"**。

---

## 第 1 步：建表 + RPC（一次性）

1. 登录 [Supabase Studio](https://supabase.com/dashboard/project/nvkcztwjlbszvwkvbetf)
2. 左侧 **SQL Editor** → **New query**
3. 打开 [`scripts/supabase/schema.sql`](schema.sql)（用 VS Code 打开，**不要从聊天复制**，否则 `$$` 会被破坏）
4. Ctrl+A 全选 → Ctrl+C → 粘贴到 SQL Editor → **Run**

执行后你会得到：
- `public.profiles` 表（username ↔ 合成 email 映射）
- `public.invitation_codes` 表（邀请码）
- 三个 RPC：`generate_invitation_codes`、`check_invitation_code`、`complete_registration`
- 对应的 RLS 策略

> 这个脚本是幂等的，重复执行不会报错。

---

## 第 2 步：关闭邮箱验证（**关键，必做**）

合成邮箱 `@users.orangeisland.local` 是不真实的，Supabase 没法（也不需要）发邮件。必须关掉"邮箱确认"才能让 signUp 直接返回 session。

1. 左侧 **Authentication** → **Sign In / Providers**
2. 展开 **Email**
3. 把 **Confirm email** 开关**关掉**（OFF）
4. **Save**

> 如果忘了关，注册会报 `email_not_confirmed_in_dashboard`。

---

## 第 3 步：生成邀请码

两种方式任选：

### 方式 A：SQL 批量生成（推荐）

**SQL Editor** 里执行：
```sql
-- 生成 10 个邀请码，每个可用 1 次，30 天后过期，备注 "beta wave 1"
select * from public.generate_invitation_codes(10, 1, now() + interval '30 days', 'beta wave 1');
```
结果会直接列出 10 个 8 位邀请码（如 `A3F9K2B7`）。

### 方式 B：Table Editor 手动加

1. 左侧 **Table Editor** → `invitation_codes` → **Insert row**
2. 填字段：
   - `code` — 自己起一个（如 `WELCOME1`），自动转大写
   - `max_uses` — 可用次数（1 = 一次性，100 = 多人共用）
   - `is_active` — 默认 `true`
   - `expires_at` — 可留空（永不过期）或填一个时间
   - `note` — 备注，自己看
3. **Save**

### 禁用邀请码

某天想停用一个码：在 Table Editor 里把对应行的 `is_active` 改成 `false` 即可。已用它注册的账号不受影响。

### 查看使用情况

Table Editor 打开 `invitation_codes`，`used_count` 列就是已用次数；接近 `max_uses` 就是快用完了。

---

## 第 4 步：凭证（已配置）

`local.properties`（gitignored）里已经填好：
```properties
SUPABASE_URL=https://nvkcztwjlbszvwkvbetf.supabase.co
SUPABASE_ANON_KEY=sb_publishable_UEd2Pn0kR2sau1Xsfk2TQw_oHC8pRhS
```

构建时会注入到 `BuildConfig`。换项目时改这两行即可。

---

## 流程说明

**注册**：用户填 用户名 + 密码 + 邀请码
1. App 调 RPC `check_invitation_code`（非消耗）确认邀请码有效
2. 调 Supabase `signUp(synthEmail, password)` —— 因 Confirm email 关闭，直接返回 session
3. 调 RPC `complete_registration` → 原子地：消耗邀请码 + 写 profiles 行
4. 登录完成，进入主界面

**登录**：用户填 用户名 + 密码
1. App 把用户名映射成合成邮箱
2. 调 Supabase `signInWith(Email, password)`
3. 登录完成

**登出**：清本地 session + Supabase signOut

---

## 故障排查

| 现象 | 原因 / 解决 |
|---|---|
| 注册时报"网络错误" | `local.properties` 里 SUPABASE_URL/KEY 没填或填错；或手机无网络 |
| 注册时报"邮箱尚未验证" | **第 2 步没做** —— 回 Authentication 把 Confirm email 关掉 |
| 注册时报"邀请码无效" | 邀请码已用完/过期/被禁用；或大小写不对（app 会自动转大写，但人工输入注意） |
| 注册时报"用户名已被占用" | 合成邮箱已被注册过；换个用户名，或去 Studio → Authentication → Users 删掉那个用户 |
| 登录时报"用户名或密码错误" | 密码错；或这个用户名从没注册过 |
| 同一用户名注册两次报错 | 这是预期行为，`profiles.username` 有 unique 约束 + 合成邮箱会撞 |

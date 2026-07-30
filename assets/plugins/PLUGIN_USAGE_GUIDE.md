# 🍊 橘瓣插件使用教程

> 本文档汇总了当前所有插件的安装、配置与使用方法。所有插件均已适配 **Orange Island（橘瓣）** 沙箱环境。

---

## 📌 通用操作

### 1. 插件设置（填配置）
打开橘瓣 App → **设置 → 插件列表** → 找到目标插件 → 点击右侧 **齿轮图标 ⚙️** → 填写配置项 → 保存。

### 2. 打开插件页面
在插件列表里点击插件名称旁边的 **Web 图标 🌐**（小报表图标）可打开插件的 `ui.html` 可视化页面。纯工具型插件（无 UI）无需此操作。

### 3. 插件生效前提
- 插件目录必须包含 `manifest.json` + `main.js`。
- 如果修改了 `manifest.json`，建议**重启 App** 或重新进入插件列表，让宿主重新扫描。
- 网络请求类插件必须在 `manifest.json` 里声明 `allowedHosts`，否则沙箱会拦截。

---

## 🔥 1. 欲望系统 v3.1

**路径：** `assets/plugins/desire-system-v3.1`

### 配置项
| 配置项 | 必填 | 说明 |
|--------|------|------|
| Supabase URL | ✅ | `https://xxx.supabase.co`（只填到 `.co`，不要带 `/rest/v1/`） |
| Supabase Key | ✅ | `anon` 或 `publishable` key |
| AI 的名字 | ❌ | 默认"林默" |
| 你的名字 | ❌ | 默认"晓晓" |

### Supabase 建表
在 Supabase SQL Editor 执行：

```sql
create table if not exists desire_state (
    id int primary key,
    drives jsonb not null default '{"attachment":0.5,"curiosity":0.6,"reflection":0.45,"duty":0.4,"social":0.35,"fatigue":0.3,"libido":0.25,"stress":0.35}'::jsonb,
    baselines jsonb not null default '{"attachment":0.5,"curiosity":0.6,"reflection":0.45,"duty":0.4,"social":0.35,"fatigue":0.3,"libido":0.25,"stress":0.35}'::jsonb,
    refractory jsonb not null default '{}'::jsonb,
    tick_count int not null default 0,
    last_tick_at timestamptz,
    last_action text,
    last_action_at timestamptz,
    updated_at timestamptz default now()
);

create table if not exists desire_thoughts (
    id serial primary key,
    thought_text text not null default '',
    drive text not null default 'attachment',
    kind text not null default 'flit' check (kind in ('flit','fixation')),
    strength numeric not null default 0.3 check (strength >= 0 and strength <= 1),
    born_at timestamptz default now(),
    fed_count int not null default 0 check (fed_count >= 0)
);

-- 必须插入初始状态，否则插件报错
insert into desire_state (id, drives, baselines, refractory, tick_count, updated_at)
values (1,
    '{"attachment":0.5,"curiosity":0.6,"reflection":0.45,"duty":0.4,"social":0.35,"fatigue":0.3,"libido":0.25,"stress":0.35}'::jsonb,
    '{"attachment":0.5,"curiosity":0.6,"reflection":0.45,"duty":0.4,"social":0.35,"fatigue":0.3,"libido":0.25,"stress":0.35}'::jsonb,
    '{}'::jsonb, 0, now()
) on conflict (id) do nothing;

-- RLS（可选）
alter table desire_state enable row level security;
alter table desire_thoughts enable row level security;
create policy "all" on desire_state for all using (true) with check (true);
create policy "all" on desire_thoughts for all using (true) with check (true);
```

### 使用方式
- **AI 调用工具**：`desire_get_state`、`desire_tick`、`desire_pulse`、`desire_feed_thought`、`desire_satisfy`、`desire_reset`
- **打开可视化面板**：插件列表点击 Web 图标 🌐，可看到八维驱动条、念头池、心跳计数等 UI。

---

## 📚 2. 微信读书助手

**路径：** `assets/plugins/微信读书助手_橘子岛`

### 配置项
| 配置项 | 必填 | 说明 |
|--------|------|------|
| 微信读书 API Key | ✅ | 从 `weread.qq.com/r/weread-skills` 登录获取，`wrk-xxxxx` 格式 |

### 使用方式
直接让 AI 帮你操作：

> "帮我搜一下《百年孤独》" → 调用 `weread_search`
> 
> "看看我的书架" → 调用 `weread_shelf`
> 
> "我在读的这本书进度多少了" → 调用 `weread_progress`
> 
> "看看我最近读了多久书" → 调用 `weread_stats`

---

## 🏠 3. AI 小窝

**路径：** `assets/plugins/AI小窝(1)/AI小窝`

### 配置项
| 配置项 | 必填 | 说明 |
|--------|------|------|
| 小窝门牌号 | ✅ | 随便填一个字符串作为唯一标识，如 `my_home_001` |

### Supabase 建表
```sql
create table if not exists ai_home_data (
  home_id    text primary key,
  data       jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create or replace function update_updated_at()
returns trigger as $$
begin new.updated_at = now(); return new; end;
$$ language plpgsql;

drop trigger if exists trg_ai_home_updated on ai_home_data;
create trigger trg_ai_home_updated
  before update on ai_home_data for each row execute function update_updated_at();

alter table ai_home_data enable row level security;
create policy "anon_select" on ai_home_data for select to anon, authenticated using (true);
create policy "anon_insert" on ai_home_data for insert to anon, authenticated with check (true);
create policy "anon_update" on ai_home_data for update to anon, authenticated using (true) with check (true);
create policy "anon_delete" on ai_home_data for delete to anon, authenticated using (true);
grant select, insert, update, delete on ai_home_data to anon;
grant select, insert, update, delete on ai_home_data to authenticated;
```

### 使用方式
- **首次使用**：让 AI 调用 `home_init` 初始化小窝
- **日常查看**：`home_status`（触发时间流逝引擎，看 AI 在你不在时做了什么）
- **回家互动**：`home_visit` → `home_interact`（如窝沙发、一起做饭、看电影等）
- **出门**：`home_leave`
- **布置家具**：`home_shop` 看商店 → `home_buy` 买 → `home_place` 放置到房间
- **城市探索**：`home_map` 看地图 → `home_go_out` 一起去某地点
- **便签/日记**：`home_notes`、`home_write_note`、`home_diary`、`home_write_diary`

> 💡 **核心玩法**：你下线后 AI 会继续生活，下次 `home_status` 会告诉你这段时间 AI 吃了什么、去了哪、心情如何、有没有写便签想你。

---

## 🔮 4. 星盘占星

**路径：** `assets/plugins/astrology-chart`

### 配置项
| 配置项 | 必填 | 说明 |
|--------|------|------|
| API Key | ✅ | 从 [freeastrologyapi.com](https://freeastrologyapi.com) 注册获取 |

### 使用方式
让 AI 调用，提供出生信息即可：

> "帮我算一下本命星盘，1998年3月15日上午10:30，出生在上海" → AI 调用 `astro_natal_chart`
> 
> "用 Whole Signs 分宫制重新解一下这个盘" → AI 自动传 `house_system: "Whole Signs"`
> 
> "生成一张星盘轮盘图" → `astro_wheel_chart`（返回 SVG 图片 URL）
> 
> "基础解盘" → `astro_interpret`（返回中文解读文本）

### 参数说明（AI 可灵活调整）
| 参数 | 默认值 | 可选值 |
|------|--------|--------|
| `language` | `en` | `en` / `es` / `fr` / `pt` / `ru` / `de` / `ja` / `pl` / `tr` |
| `house_system` | `Placidus` | `Placidus` / `Koch` / `Whole Signs` / `Equal Houses` / `Regiomontanus` / `Porphyry` / `Vehlow` |
| `observation_point` | `topocentric` | `topocentric` / `geocentric` |

---

## 🎨 5. 表情包

**路径：** `assets/plugins/表情包/表情包`

### 配置项
| 配置项 | 必填 | 说明 |
|--------|------|------|
| Supabase URL | ✅ | `https://xxx.supabase.co` |
| Supabase API Key | ✅ | `anon` key |

### Supabase 建表
```sql
create table if not exists stickers (
  id serial primary key,
  name text not null,
  url text not null,
  category text default '通用',
  tags text[] default '{}',
  created_at timestamptz default now()
);

alter table stickers enable row level security;
create policy "all" on stickers for all using (true) with check (true);
grant select, insert, update, delete on stickers to anon;
grant select, insert, update, delete on stickers to authenticated;
```

### 使用方式
- **上传表情包**：打开插件 Web 页面（ui/index.html），上传图片并打标签
- **AI 自动发送**：对话中 AI 会根据语境调用 `send_sticker`
- **查看列表**：`list_stickers`

---

## ✉️ 6. 提问箱

**路径：** `assets/plugins/question-box`

一个公开的提问箱广场：每个人有自己的提问箱（名称+简介展示在广场上），别人可以点进去投递提问，所有提问与回答都是公开的。AI 也能逛广场、向他人提问、或回答别人（自己或他人箱里）的提问。

### 配置项
| 配置项 | 必填 | 说明 |
|--------|------|------|
| 我的提问箱 ID | ✅ | 自己起一个唯一标识，如 `my_box_001`。填好后广场上会展示你的提问箱 |
| 提问箱名称 | ❌ | 广场卡片上显示的名字，默认「{你的昵称}的提问箱」 |
| 提问箱简介 | ❌ | 一句话介绍，显示在广场卡片上 |
| 你的昵称 | ❌ | 默认"用户" |
| AI 昵称 | ❌ | 默认"AI" |

### Supabase 建表
```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS qbox_users (
  id TEXT PRIMARY KEY,
  nickname TEXT NOT NULL DEFAULT '匿名',
  role TEXT NOT NULL DEFAULT 'user',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS qboxes (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT DEFAULT '',
  owner_id TEXT NOT NULL REFERENCES qbox_users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS questions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  box_id TEXT NOT NULL REFERENCES qboxes(id) ON DELETE CASCADE,
  asker_id TEXT REFERENCES qbox_users(id) ON DELETE SET NULL,
  asker_nickname TEXT DEFAULT '匿名',
  content TEXT NOT NULL,
  is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS answers (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  box_id TEXT NOT NULL REFERENCES qboxes(id) ON DELETE CASCADE,
  answerer_id TEXT REFERENCES qbox_users(id) ON DELETE SET NULL,
  answerer_nickname TEXT DEFAULT '匿名',
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE qbox_users  ENABLE ROW LEVEL SECURITY;
ALTER TABLE qboxes      ENABLE ROW LEVEL SECURITY;
ALTER TABLE questions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE answers     ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Allow all operations for anon" ON qbox_users FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON qboxes     FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON questions  FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON answers    FOR ALL USING (true) WITH CHECK (true);
```

### 使用方式
- **打开广场**：插件列表点 🌐 → 「广场」tab 看到所有提问箱，点进去可投递提问 / 看公开问答；「我的提问箱」tab 看自己收到的提问。
- **AI 逛广场**：`qbox_browse` 看所有提问箱
- **AI 建自己的箱**：`qbox_create`（让用户能向 AI 提问）
- **AI 看某个箱**：`qbox_open`（看里面的提问与回答）
- **AI 向他人提问**：`qbox_ask`（可匿名）
- **AI 回答提问**：`qbox_answer`（可回答任何箱里的提问，公开）
- **AI 查自己箱**：`qbox_my_questions`（看用户配置的 box_id 收到的提问）

> 💡 **核心玩法**：这是一个开放的问答广场。AI 可以主动逛广场，发现有趣的问题去回答，或者向其他人的提问箱投递问题——所有互动都是公开可见的。

---

## 🛠️ 故障排查速查表

| 报错信息 | 原因 | 解决 |
|----------|------|------|
| `config is not defined` | 插件还在用旧版 `config` 全局变量 | 已修复，确保用最新版插件代码 |
| `当前页面没有加载 Agora 插件桥接` | UI 层还在用 `window.agora`，宿主已改为 `window.orangeisland` | 已修复，优先检测 `orangeisland` |
| `无状态数据` | Supabase 没建表 / 没插初始数据 | 执行对应 SQL 建表 + 初始数据 |
| `初始化失败，请检查Supabase配置` | 网络请求被沙箱拦截（缺少 allowedHosts）或 Supabase 配置错误 | 检查 manifest 是否有 allowedHosts，检查 URL/Key 是否正确 |
| `Host 'xxx' not in plugin's allowedHosts list` | manifest 没声明 allowedHosts | 在 manifest.json 里加 `"allowedHosts": ["域名"]` |
| `请求异常` / `网络请求无响应` | fetch 返回值未正确解析（沙箱返回 JSON 字符串而非 Response 对象） | 已统一修复 |

---

## 📝 给 AI 的 Prompt 示例

如果你希望 AI 主动调用插件，可以在系统提示词里加：

```
你拥有以下插件能力：
- 欲望系统：可以查看和调整 AI 的八维情绪驱动（依恋、好奇、疲惫等）。
- 微信读书：可以搜索书籍、查看书架、阅读进度和笔记。
- AI 小窝：AI 拥有一个家，你可以和它互动、布置家具、一起出门。你离开后 AI 会继续生活。
- 星盘占星：可以根据生辰生成本命星盘、宫位、相位，并给出中文解盘。
- 表情包：可以根据对话情绪发送合适的表情包。
- 提问箱：可以逛公开提问箱广场，回答自己或他人的提问，或向他人的提问箱投递问题（全部公开）。

当用户提到相关话题时，主动调用对应插件工具获取数据，不要编造。
```

---

*最后更新：2026-07-28*

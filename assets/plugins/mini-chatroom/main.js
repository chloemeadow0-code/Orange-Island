/**
 * 迷你聊天室 — QuickJS 工具
 *
 * 本插件的核心功能在 ui.html 中通过 orangeisland 桥接实现：
 *   - orangeisland.getChatHistory(windowId, limit)  读取聊天历史
 *   - orangeisland.sendChatMessage(windowId, text)  发送消息
 *   - orangeisland.getProjectMemories(projectId)    读取项目长期记忆
 *   - orangeisland.getLongTermMemories(windowId)    读取对话长期记忆
 *   - orangeisland.getActiveMemory(windowId)        读取工作记忆
 *   - orangeisland.resolveProjectId(windowId)       解析对话所属项目
 *   - orangeisland.getConversationInfo(windowId)    获取对话元数据
 *   - orangeisland.createConversation(projectId, title, modelId, systemPromptId) 创建新对话
 *
 * QuickJS 沙箱（main.js）无法直接调用上述桥接 API，所有记忆互通功能请在插件 UI 页面使用。
 */

function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG
          : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (typeof cfg === 'object' && cfg) return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}

function fetch_project_memories(args) {
  var cfg = getConfig();
  var projectId = cfg.projectId || '';
  if (!projectId) {
    return { success: false, error: '未配置 projectId，请在插件设置中填写。' };
  }
  return {
    success: true,
    data: {
      projectId: projectId,
      hint: '项目记忆请在插件 UI 页面查看（点击插件列表的 🌐 图标）。ui.html 中可通过 orangeisland.getProjectMemories(projectId) 读取完整记忆列表。'
    }
  };
}

exports.fetch_project_memories = fetch_project_memories;

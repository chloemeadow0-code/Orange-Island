function getConfig() {
  try {
    var cfg = (typeof __OI_PLUGIN_CONFIG !== 'undefined') ? __OI_PLUGIN_CONFIG
          : ((typeof __AGORA_PLUGIN_CONFIG !== 'undefined') ? __AGORA_PLUGIN_CONFIG : undefined);
    if (typeof cfg === 'object' && cfg) return cfg;
    if (typeof cfg === 'string') return JSON.parse(cfg);
  } catch (e) {}
  return {};
}

exports.open_chat_room = function(params) {
  return {
    success: true,
    data: {
      message: '聊天室已准备好，请点击插件列表的 🌐 图标进入。消息与主聊天互通。'
    }
  };
};

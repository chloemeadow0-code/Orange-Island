(function () {
  const storageKey = "orange-island-ui-playground-v4";
  const presets = {
    sunlight: {
      accent: "#f28a34",
      leaf: "#78966c",
      cream: "#fffbf5",
      radius: 24,
      density: 100,
      texture: true
    },
    breeze: {
      accent: "#e68b52",
      leaf: "#5e9488",
      cream: "#f8fcf7",
      radius: 28,
      density: 96,
      texture: true
    },
    dusk: {
      accent: "#d8753d",
      leaf: "#7d8060",
      cream: "#fff7ef",
      radius: 20,
      density: 92,
      texture: false
    }
  };

  let state = { ...presets.sunlight, preset: "sunlight", chatView: "empty" };

  function safeRead() {
    try {
      const stored = JSON.parse(localStorage.getItem(storageKey));
      if (stored && typeof stored === "object") {
        const { chatView: _ignoredChatView, ...storedVisualState } = stored;
        state = { ...state, ...storedVisualState };
      }
    } catch (_) {}
  }

  function safeWrite() {
    try {
      const { chatView: _sessionOnlyChatView, ...visualState } = state;
      localStorage.setItem(storageKey, JSON.stringify(visualState));
    } catch (_) {}
  }

  function mix(hex, target, amount) {
    const clean = hex.replace("#", "");
    const full = clean.length === 3 ? clean.split("").map((c) => c + c).join("") : clean;
    const value = parseInt(full, 16);
    const targetValue = parseInt(target.replace("#", ""), 16);
    const channels = [16, 8, 0].map((shift) => {
      const from = (value >> shift) & 255;
      const to = (targetValue >> shift) & 255;
      return Math.round(from + (to - from) * amount);
    });
    return `#${channels.map((v) => v.toString(16).padStart(2, "0")).join("")}`;
  }

  function applyState(persist = true) {
    const root = document.documentElement;
    const chatView = state.chatView === "conversation" ? "conversation" : "empty";
    state.chatView = chatView;
    root.style.setProperty("--accent", state.accent);
    root.style.setProperty("--accent-deep", mix(state.accent, "#6d2a12", 0.48));
    root.style.setProperty("--accent-soft", mix(state.accent, "#ffffff", 0.7));
    root.style.setProperty("--leaf", state.leaf);
    root.style.setProperty("--leaf-soft", mix(state.leaf, "#ffffff", 0.72));
    root.style.setProperty("--cream", state.cream);
    root.style.setProperty("--radius", `${state.radius}px`);
    root.style.setProperty("--density", String(state.density / 100));
    root.style.setProperty("--texture-opacity", state.texture ? "0.78" : "0");

    document.querySelectorAll("[data-preset]").forEach((button) => {
      const active = button.dataset.preset === state.preset;
      button.classList.toggle("active", active);
      button.setAttribute("aria-pressed", String(active));
    });

    document.body.classList.toggle("chat-view-empty", chatView === "empty");
    document.body.classList.toggle("chat-view-conversation", chatView === "conversation");
    document.querySelectorAll("[data-chat-view]").forEach((button) => {
      const active = button.dataset.chatView === chatView;
      button.classList.toggle("active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    document.querySelectorAll("[data-chat-state-label]").forEach((node) => {
      node.textContent = chatView === "empty"
        ? "初始空页面 · 布局锁定"
        : "已有对话 · 布局锁定";
    });
    document.querySelectorAll("[data-chat-input]").forEach((input) => {
      input.placeholder = chatView === "empty"
        ? "向 橘子岛 提问…"
        : "给克老师发送消息…";
    });

    const accent = document.querySelector("[data-control='accent']");
    const leaf = document.querySelector("[data-control='leaf']");
    const radius = document.querySelector("[data-control='radius']");
    const density = document.querySelector("[data-control='density']");
    const texture = document.querySelector("[data-control='texture']");
    if (accent) accent.value = state.accent;
    if (leaf) leaf.value = state.leaf;
    if (radius) radius.value = state.radius;
    if (density) density.value = state.density;
    if (texture) {
      texture.classList.toggle("on", state.texture);
      texture.setAttribute("aria-pressed", String(state.texture));
    }
    document.querySelectorAll("[data-output='radius']").forEach((node) => node.textContent = `${state.radius}px`);
    document.querySelectorAll("[data-output='density']").forEach((node) => node.textContent = `${state.density}%`);
    if (persist) safeWrite();
  }

  let toastTimer;
  function toast(message) {
    let node = document.querySelector(".toast");
    if (!node) {
      node = document.createElement("div");
      node.className = "toast";
      node.setAttribute("role", "status");
      document.body.appendChild(node);
    }
    node.textContent = message;
    node.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => node.classList.remove("show"), 1600);
  }

  function bindTuner() {
    document.querySelectorAll("[data-preset]").forEach((button) => {
      button.addEventListener("click", () => {
        const preset = button.dataset.preset;
        state = { ...presets[preset], preset, chatView: state.chatView };
        applyState();
        toast(`已切换为${button.querySelector("strong").textContent}`);
      });
    });

    document.querySelectorAll("[data-chat-view]").forEach((button) => {
      button.addEventListener("click", () => {
        document.body.classList.remove("chat-live-start");
        state.chatView = button.dataset.chatView === "conversation" ? "conversation" : "empty";
        applyState();
        toast(state.chatView === "empty" ? "正在查看初始空页面" : "正在查看已有对话");
      });
    });

    document.querySelectorAll("[data-control]").forEach((control) => {
      const key = control.dataset.control;
      if (key === "texture") {
        control.addEventListener("click", () => {
          state.texture = !state.texture;
          state.preset = "custom";
          applyState();
        });
        return;
      }
      control.addEventListener("input", () => {
        state[key] = key === "radius" || key === "density" ? Number(control.value) : control.value;
        state.preset = "custom";
        applyState();
      });
    });

    document.querySelectorAll("[data-reset]").forEach((button) => {
      button.addEventListener("click", () => {
        state = { ...presets.sunlight, preset: "sunlight", chatView: "empty" };
        document.body.classList.remove("chat-live-start");
        applyState();
        toast("已恢复默认视觉与初始页面");
      });
    });

    const mobileButton = document.querySelector(".mobile-tuner-button");
    const tuner = document.querySelector(".tuner");
    if (mobileButton && tuner) {
      mobileButton.addEventListener("click", () => {
        const open = tuner.classList.toggle("mobile-open");
        mobileButton.setAttribute("aria-expanded", String(open));
        mobileButton.textContent = open ? "×" : "调";
      });
    }
  }

  function bindDemoControls() {
    document.querySelectorAll(".switch[data-setting]").forEach((button) => {
      button.addEventListener("click", (event) => {
        event.stopPropagation();
        const on = button.classList.toggle("on");
        button.setAttribute("aria-pressed", String(on));
        toast(`${button.dataset.setting}${on ? "已开启" : "已关闭"}`);
      });
    });

    document.querySelectorAll("[data-demo]").forEach((button) => {
      button.addEventListener("click", () => toast(button.dataset.demo || "这是视觉原型中的示意交互"));
    });

    const trayButton = document.querySelector("[data-tool-toggle]");
    const tray = document.querySelector(".tool-tray");
    if (trayButton && tray) {
      trayButton.addEventListener("click", () => {
        const open = tray.classList.toggle("open");
        trayButton.setAttribute("aria-expanded", String(open));
      });
    }

    const form = document.querySelector("[data-chat-form]");
    const input = document.querySelector("[data-chat-input]");
    const scroll = document.querySelector(".chat-scroll");
    if (form && input && scroll) {
      form.addEventListener("submit", (event) => {
        event.preventDefault();
        const value = input.value.trim();
        if (!value) return;
        const startingFromEmpty = state.chatView !== "conversation";
        if (startingFromEmpty) {
          document.body.classList.add("chat-live-start");
          state.chatView = "conversation";
          applyState();
        }
        const row = document.createElement("div");
        row.className = "message-row user";
        const wrap = document.createElement("div");
        wrap.className = "bubble-wrap";
        const bubble = document.createElement("div");
        bubble.className = "message-bubble";
        bubble.textContent = value;
        const meta = document.createElement("div");
        meta.className = "message-meta";
        meta.textContent = "刚刚 · 已发送";
        wrap.append(bubble, meta);
        row.appendChild(wrap);
        scroll.appendChild(row);
        input.value = "";
        scroll.scrollTo({ top: scroll.scrollHeight, behavior: "smooth" });
        setTimeout(() => {
          const reply = document.createElement("div");
          reply.className = "message-row";
          reply.innerHTML = '<div class="avatar-fruit" aria-hidden="true"></div><div class="bubble-wrap"><div class="message-name">克老师</div><div class="message-bubble">收到。原型里的对话交互已经接上了，视觉参数也会在三个页面之间保留。</div><div class="message-meta">刚刚</div></div>';
          scroll.appendChild(reply);
          scroll.scrollTo({ top: scroll.scrollHeight, behavior: "smooth" });
        }, 520);
      });

      input.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
          event.preventDefault();
          form.requestSubmit();
        }
      });
    }
  }

  safeRead();
  document.addEventListener("DOMContentLoaded", () => {
    bindTuner();
    bindDemoControls();
    applyState(false);
  });
})();

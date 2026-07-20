# Sandbox

橘子岛 可以在您的裝置上本機執行輕量級的 Alpine Linux 環境 — 不需要網際網路連線。Sandbox 讓模型能夠在隔離的 root 檔案系統中安裝套件並執行指令。

!!! note "可用性"
    Sandbox 在所有版本中皆可用。可從 **設定 → Shell → Sandbox 管理** 存取，或直接透過 **設定 → Sandbox** 存取。

## 運作方式

Sandbox 使用部署到您裝置應用程式私有儲存空間的 Alpine Linux root 檔案系統。一個基於 `apk` 的最小化套件管理器讓您能夠在此環境中安裝軟體，而指令則在基於 proot 的容器內執行。

這**不是**完整的虛擬機器 — 它是一個輕量級的使用者空間容器，共用主機核心。它提供足夠的隔離性以進行安全實驗，同時保持低資源使用量。

---

## VPN 干擾 — 重要

!!! danger "使用 Sandbox 網路功能前請關閉您的 VPN"

    VPN 應用程式會干擾 proot 的 DNS 解析。原因如下：

    **根本原因 — PRoot 沒有網路命名空間隔離。**

    PRoot 使用 `ptrace` 來攔截系統呼叫並重新導向檔案路徑，但它**不**支援 `CLONE_NEWNET`（Linux 網路命名空間）。Sandbox 內的所有處理程序直接共用主機 Android 系統的網路堆疊。沒有虛擬網路介面、沒有隔離的路由表，也沒有獨立的 DNS 設定。

    **Android 上的 VPN 如何破壞 proot 內的 DNS：**

    1. Android VPN 應用程式使用 `VpnService` API，它會建立一個 **TUN 介面** — 一個虛擬網路裝置，會攔截**所有**裝置流量，包括來自 proot 內部的流量
    2. 為防止 DNS 洩漏到加密隧道之外，VPN 會**將所有埠號 53（DNS）流量重新導向**到它自己的 DNS 伺服器
    3. 在 proot 內部，當應用程式呼叫 `getaddrinfo()`（標準 libc DNS 解析器）時，請求會透過 Android 的系統解析器 — 而該解析器已被 VPN 攔截
    4. 在 Android 12+ 上，Google 重新設計了 DNS 解析器，使得 proot 環境中的 `getaddrinfo()` 特別脆弱（[termux/proot#215](https://github.com/termux/proot/issues/215)）
    5. VPN 的 TUN 路由與系統解析器的 DNS 路徑在 proot 內發生衝突：解析器發送 DNS 查詢，VPN TUN 攔截它，但回應永遠無法透過 proot 的 `ptrace` 層返回

    **觀察到的症狀：**

    | 操作 | 結果 |
    |-----------|--------|
    | `ping 1.1.1.1` | ✅ 正常（直接 IP，不需要 DNS） |
    | `ping google.com` | ❌ 失敗 — "Temporary failure in name resolution" |
    | `apk add python3` | ❌ 失敗 — 無法解析 `dl-cdn.alpinelinux.org` |
    | `curl https://example.com` | ❌ 失敗 — 名稱解析錯誤 |
    | `curl https://1.1.1.1` | ✅ 正常（IP 直接連線） |

    **修復方法：** 在 Sandbox 中執行任何網路操作（安裝套件、`curl`、`wget` 等）之前，請完全關閉您的 VPN。網路操作完成後，您可以重新啟用 VPN。

    這是 proot 架構的基本限制 — 當 Android VPN 透過 TUN 介面覆寫系統的 DNS 路由時，它無法虛擬化網路堆疊。

---

## 設定

### 安裝 Root 檔案系統

首次開啟 Sandbox 時，您會看到一個儀表板，顯示 rootfs 尚未安裝。點選 **安裝** 以下載並解壓縮 Alpine root 檔案系統。

!!! info "儲存空間使用量"
    基礎 rootfs 使用約 100–200 MB。已安裝的套件會消耗額外空間。總磁碟使用量會顯示在儀表板上。

---

## 套件管理

### 安裝套件

1. 在文字欄位中輸入套件名稱（例如 `python3`）
2. 點選 **安裝**
3. 觀看終端機輸出以了解安裝進度

或者，點選常用套件的**快速安裝晶片**：

```
python3   git      curl      wget
openssh   nodejs   build-base   htop
```

### 已安裝的套件

在安裝區塊下方，所有已安裝的套件會列出其：

- **名稱** — Alpine 套件名稱
- **版本** — 已安裝的版本
- **描述** — 簡短摘要（截斷顯示）

### 移除套件

點選任何已安裝套件上的 :material-close: 圖示來移除它。刪除前會出現確認對話框。

---

## 儀表板

當 Sandbox 準備就緒時，儀表板會顯示：

- **磁碟使用量** — 進度條與數字顯示（MB 或 GB）
- **已安裝數量** — 套件總數

---

## 終端機輸出

安裝或移除套件時，終端機輸出會顯示在輸入欄位下方的一個深色主題、可捲動的等寬字型檢視中。輸出會自動捲動以跟隨最新行。

使用此功能來：
- 監控安裝進度
- 偵錯失敗的套件操作
- 查看套件安裝了哪些檔案

---

## 重設 Sandbox

底部的**危險區域**包含一個**重設 Sandbox** 選項。這會完全移除 root 檔案系統及所有已安裝的套件。

!!! danger "破壞性操作"
    重設 Sandbox 會刪除整個 Alpine 環境。之後您將需要重新安裝 rootfs 及所有套件。確認對話框可防止意外重設。

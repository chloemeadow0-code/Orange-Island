# 网络隔离诊断 v1.0.0

这个 ZIP 只做隔离排查，不会搜索歌曲，不会打开网易云，不会触发 deeplink。

## 它测试两条线

### 1. main.js 宿主 fetch

点击“主机”页会调用 `diagnose_main_fetch`，测试：

- `https://example.com/`
- `https://music.163.com/`
- `https://music.163.com/api/search/get?...`

### 2. ui.html 浏览器网络

点击“浏览器”页会用 `fetch(..., { mode: 'no-cors' })` 测试同样三个地址。

`no-cors` 不读响应体，只看请求能不能完成。

## 结果怎么判断

### main.js 全失败，UI 成功

宿主 `fetch` 这条桥有问题。

### main.js 和 UI 都失败

更像设备网络、WebView 网络策略，或者橘子岛整体网络被拦住。

### example.com 成功，但网易云失败

优先查网易云域名、证书、接口限制，或网易云请求头。

### 网易云首页成功，但搜索失败

优先查网易云搜索接口兼容性。

## 你现在这组结果意味着什么

你之前贴出来的结果里，连 `example.com` 都是 `HTTP: 0`，这已经把问题从“网易云接口”推进到了“橘子岛主机 fetch / 宿主网络层”。

这个隔离包会帮你再确认一次：

- 是不是只有 main.js 的 fetch 有问题
- 还是 ui.html 的浏览器网络也一起坏了
- 还是网易云专属域名被拦

把两个页面的 probe 结果发回来，就能继续往下收敛。

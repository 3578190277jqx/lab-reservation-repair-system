// 公网服务器地址：开发调试时，不同 Wi-Fi 或手机流量均连接同一台 ECS。
// HTTP IP 仅用于开发调试：开发者工具需关闭合法域名校验，手机需开启调试。
const developmentURL = 'http://127.0.0.1:8088/api'

// 正式发布前填写已配置到微信后台的 HTTPS 域名（包含 /api）。
// 同一域名需配置 request、uploadFile、downloadFile 合法域名。
// 示例：https://lab.example.com/api。正式版不回退到 HTTP 地址。
const productionURL = ''

let version = 'develop'
try {
  if (typeof wx !== 'undefined' && wx.getAccountInfoSync) {
    version = wx.getAccountInfoSync().miniProgram.envVersion
  }
} catch (_) {}

module.exports = {
  baseURL: version === 'develop' ? developmentURL : productionURL
}
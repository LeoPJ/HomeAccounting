const { request } = require('./request.js')

/** 已登录且已加入家庭则 resolve 家庭摘要；否则跳转并重置流程 */
function ensureHousehold() {
  const token = wx.getStorageSync('token')
  if (!token) {
    wx.reLaunch({ url: '/pages/me/me' })
    return Promise.reject(new Error('no token'))
  }
  return request({ url: '/api/v1/households/me', method: 'GET' }).then((body) => {
    if (!body.data.joined) {
      wx.redirectTo({ url: '/pages/household/household' })
      return Promise.reject(new Error('no household'))
    }
    return body.data
  })
}

module.exports = {
  ensureHousehold,
}

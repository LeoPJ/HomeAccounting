const { request } = require('./utils/request.js')

App({
  globalData: {},

  onLaunch() {
    const token = wx.getStorageSync('token')
    if (token) {
      return
    }
    wx.login({
      success: (loginRes) => {
        if (!loginRes.code) {
          return
        }
        request({
          url: '/api/v1/auth/wechat/login',
          method: 'POST',
          data: { code: loginRes.code },
        })
          .then((body) => {
            wx.setStorageSync('token', body.data.token)
            if (body.data.needsHousehold) {
              wx.redirectTo({ url: '/pages/household/household' })
            }
          })
          .catch(() => {})
      },
    })
  },
})

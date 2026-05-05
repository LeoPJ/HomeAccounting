const { request } = require('../../utils/request.js')

Page({
  data: {
    loading: true,
    profile: null,
    household: null,
    error: '',
  },

  onShow() {
    const token = wx.getStorageSync('token')
    if (!token) {
      this.setData({ loading: false, profile: null, household: null, error: '未登录' })
      return
    }
    this.setData({ loading: true, error: '' })
    Promise.all([
      request({ url: '/api/v1/me', method: 'GET' }),
      request({ url: '/api/v1/households/me', method: 'GET' }),
    ])
      .then(([me, hh]) => {
        const joined = hh.data && hh.data.joined
        if (!joined) {
          wx.redirectTo({ url: '/pages/household/household' })
          return
        }
        this.setData({
          profile: me.data || {},
          household: hh.data || {},
          loading: false,
        })
      })
      .catch(() => {
        this.setData({ loading: false, error: '加载失败' })
      })
  },

  goHousehold() {
    wx.navigateTo({ url: '/pages/household/household' })
  },

  goSettings() {
    wx.switchTab({ url: '/pages/settings/settings' })
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '清除本地登录状态？',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        wx.removeStorageSync('token')
        this.setData({ profile: null, household: null, error: '已退出' })
        wx.reLaunch({ url: '/pages/me/me' })
      },
    })
  },

  refreshLogin() {
    wx.login({
      success: (loginRes) => {
        if (!loginRes.code) {
          wx.showToast({ title: 'wx.login 失败', icon: 'none' })
          return
        }
        request({
          url: '/api/v1/auth/wechat/login',
          method: 'POST',
          data: { code: loginRes.code },
        })
          .then((body) => {
            wx.setStorageSync('token', body.data.token)
            wx.showToast({ title: '已登录', icon: 'success' })
            this.onShow()
          })
          .catch(() => {})
      },
    })
  },
})

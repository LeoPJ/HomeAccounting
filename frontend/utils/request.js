const { API_BASE } = require('../config.js')

/**
 * @param {{ url: string, method?: string, data?: object }} options
 * @returns {Promise<{ code: number, message: string, data: any }>}
 */
function request(options) {
  const token = wx.getStorageSync('token')
  const method = options.method || 'GET'
  return new Promise((resolve, reject) => {
    wx.request({
      url: API_BASE + options.url,
      method,
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
      },
      success(res) {
        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.showToast({ title: '登录已失效', icon: 'none' })
          wx.reLaunch({ url: '/pages/me/me' })
          reject(new Error('401'))
          return
        }
        const body = res.data
        if (!body || typeof body.code === 'undefined') {
          reject(new Error('响应格式错误'))
          return
        }
        if (body.code !== 0) {
          wx.showToast({ title: body.message || '请求失败', icon: 'none' })
          reject(new Error(body.message || '请求失败'))
          return
        }
        resolve(body)
      },
      fail(err) {
        wx.showToast({ title: '网络错误', icon: 'none' })
        reject(err)
      },
    })
  })
}

module.exports = {
  request,
}

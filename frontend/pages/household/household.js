const { request } = require('../../utils/request.js')

Page({
  data: {
    createName: '',
    inviteCode: '',
    creating: false,
    joining: false,
  },

  onNameInput(e) {
    this.setData({ createName: e.detail.value })
  },

  onCodeInput(e) {
    this.setData({ inviteCode: e.detail.value })
  },

  onCreate() {
    const name = (this.data.createName || '').trim()
    if (!name) {
      wx.showToast({ title: '请填写家庭名称', icon: 'none' })
      return
    }
    this.setData({ creating: true })
    request({
      url: '/api/v1/households',
      method: 'POST',
      data: { name },
    })
      .then(() => {
        wx.showToast({ title: '创建成功', icon: 'success' })
        wx.reLaunch({ url: '/pages/ledger/ledger' })
      })
      .catch(() => {})
      .then(() => {
        this.setData({ creating: false })
      })
  },

  onJoin() {
    const inviteCode = (this.data.inviteCode || '').trim()
    if (!inviteCode) {
      wx.showToast({ title: '请填写邀请码', icon: 'none' })
      return
    }
    this.setData({ joining: true })
    request({
      url: '/api/v1/households/join',
      method: 'POST',
      data: { inviteCode },
    })
      .then(() => {
        wx.showToast({ title: '加入成功', icon: 'success' })
        wx.reLaunch({ url: '/pages/ledger/ledger' })
      })
      .catch(() => {})
      .then(() => {
        this.setData({ joining: false })
      })
  },
})

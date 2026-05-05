const { request } = require('../../utils/request.js')
const { ensureHousehold } = require('../../utils/auth.js')

Page({
  data: {
    catsExpense: [],
    catsIncome: [],
    tags: [],
    newCatName: '',
    newTagName: '',
    catType: 'EXPENSE',
    loading: true,
    savingCat: false,
    savingTag: false,
  },

  onShow() {
    ensureHousehold()
      .then(() => this.loadAll())
      .catch(() => this.setData({ loading: false }))
  },

  loadAll() {
    this.setData({ loading: true })
    Promise.all([
      request({ url: '/api/v1/categories', method: 'GET', data: { type: 'EXPENSE' } }),
      request({ url: '/api/v1/categories', method: 'GET', data: { type: 'INCOME' } }),
      request({ url: '/api/v1/tags', method: 'GET' }),
    ])
      .then(([ce, ci, tg]) => {
        this.setData({
          catsExpense: ce.data || [],
          catsIncome: ci.data || [],
          tags: tg.data || [],
          loading: false,
        })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  onCatTypeExpense() {
    this.setData({ catType: 'EXPENSE' })
  },

  onCatTypeIncome() {
    this.setData({ catType: 'INCOME' })
  },

  onNewCatInput(e) {
    this.setData({ newCatName: e.detail.value })
  },

  onNewTagInput(e) {
    this.setData({ newTagName: e.detail.value })
  },

  addCategory() {
    const name = (this.data.newCatName || '').trim()
    if (!name) {
      wx.showToast({ title: '请输入分类名', icon: 'none' })
      return
    }
    if (this.data.savingCat) {
      return
    }
    this.setData({ savingCat: true })
    request({
      url: '/api/v1/categories',
      method: 'POST',
      data: {
        type: this.data.catType,
        name,
        sortOrder: 0,
        enabled: true,
      },
    })
      .then(() => {
        wx.showToast({ title: '已添加', icon: 'success' })
        this.setData({ newCatName: '' })
        return this.loadAll()
      })
      .catch(() => {})
      .then(() => {
        this.setData({ savingCat: false })
      })
  },

  deleteCategory(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除分类',
      content: '确定删除？若已被流水引用可能失败。',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        request({ url: '/api/v1/categories/' + id, method: 'DELETE' })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' })
            return this.loadAll()
          })
          .catch(() => {})
      },
    })
  },

  addTag() {
    const name = (this.data.newTagName || '').trim()
    if (!name) {
      wx.showToast({ title: '请输入标签名', icon: 'none' })
      return
    }
    if (this.data.savingTag) {
      return
    }
    this.setData({ savingTag: true })
    request({ url: '/api/v1/tags', method: 'POST', data: { name } })
      .then(() => {
        wx.showToast({ title: '已添加', icon: 'success' })
        this.setData({ newTagName: '' })
        return this.loadAll()
      })
      .catch(() => {})
      .then(() => {
        this.setData({ savingTag: false })
      })
  },

  deleteTag(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '删除标签',
      content: '确定删除？',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        request({ url: '/api/v1/tags/' + id, method: 'DELETE' })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' })
            return this.loadAll()
          })
          .catch(() => {})
      },
    })
  },
})

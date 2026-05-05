const { request } = require('../../utils/request.js')
const { ensureHousehold } = require('../../utils/auth.js')

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function todayDateStr() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function nowTimeStr() {
  const d = new Date()
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

Page({
  data: {
    ledgers: [],
    ledgerIdx: 0,
    funds: [],
    fundPickerRange: ['（不计入余额）'],
    fundIdx: 0,
    categories: [],
    categoryIdx: 0,
    type: 'EXPENSE',
    tags: [],
    selectedTagIds: [],
    amount: '',
    note: '',
    dateStr: todayDateStr(),
    timeStr: nowTimeStr(),
    loading: true,
    submitting: false,
    tagGroupKey: 0,
  },

  onShow() {
    ensureHousehold()
      .then(() => this.loadAll())
      .catch(() => this.setData({ loading: false }))
  },

  loadAll() {
    this.setData({ loading: true })
    Promise.all([
      request({ url: '/api/v1/ledgers', method: 'GET' }),
      request({ url: '/api/v1/fund-accounts', method: 'GET' }),
      request({ url: '/api/v1/categories', method: 'GET', data: { type: this.data.type } }),
      request({ url: '/api/v1/tags', method: 'GET' }),
    ])
      .then(([lg, fd, cat, tg]) => {
        const ledgers = lg.data || []
        const funds = fd.data || []
        const fundPickerRange = ['（不计入余额）'].concat(funds.map((x) => x.name))
        const categories = cat.data || []
        const tags = tg.data || []
        this.setData({
          ledgers,
          funds,
          fundPickerRange,
          categories,
          tags,
          ledgerIdx: 0,
          fundIdx: 0,
          categoryIdx: 0,
          loading: false,
        })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  reloadCategories() {
    request({
      url: '/api/v1/categories',
      method: 'GET',
      data: { type: this.data.type },
    }).then((cat) => {
      const categories = cat.data || []
      this.setData({ categories, categoryIdx: 0 })
    })
  },

  onTypeExpense() {
    this.setData({ type: 'EXPENSE' })
    this.reloadCategories()
  },

  onTypeIncome() {
    this.setData({ type: 'INCOME' })
    this.reloadCategories()
  },

  onLedgerChange(e) {
    this.setData({ ledgerIdx: Number(e.detail.value) })
  },

  onFundChange(e) {
    this.setData({ fundIdx: Number(e.detail.value) })
  },

  onCategoryChange(e) {
    this.setData({ categoryIdx: Number(e.detail.value) })
  },

  onTagsChange(e) {
    const raw = e.detail.value || []
    this.setData({
      selectedTagIds: raw.map((x) => Number(x)),
    })
  },

  onAmount(e) {
    this.setData({ amount: e.detail.value })
  },

  onNote(e) {
    this.setData({ note: e.detail.value })
  },

  onDateChange(e) {
    this.setData({ dateStr: e.detail.value })
  },

  onTimeChange(e) {
    this.setData({ timeStr: e.detail.value })
  },

  onSubmit() {
    const {
      ledgers,
      ledgerIdx,
      funds,
      fundIdx,
      categories,
      categoryIdx,
      type,
      amount,
      note,
      dateStr,
      timeStr,
      selectedTagIds,
      submitting,
    } = this.data
    if (submitting) {
      return
    }
    if (!ledgers.length) {
      wx.showToast({ title: '请先创建账本', icon: 'none' })
      return
    }
    if (!categories.length) {
      wx.showToast({ title: '请先创建分类', icon: 'none' })
      return
    }
    const amt = Number(amount)
    if (!amount || Number.isNaN(amt) || amt <= 0) {
      wx.showToast({ title: '请输入正确金额', icon: 'none' })
      return
    }
    const ledgerId = ledgers[ledgerIdx].id
    let fundAccountId = null
    if (fundIdx > 0 && funds.length) {
      fundAccountId = funds[fundIdx - 1].id
    }
    const categoryId = categories[categoryIdx].id
    const occurredAt = `${dateStr}T${timeStr}:00`
    const payload = {
      ledgerId,
      fundAccountId,
      categoryId,
      type,
      amount: amt,
      occurredAt,
      tagIds: selectedTagIds,
    }
    if (note && note.trim()) {
      payload.note = note.trim()
    }
    this.setData({ submitting: true })
    request({ url: '/api/v1/transactions', method: 'POST', data: payload })
      .then(() => {
        wx.showToast({ title: '已保存', icon: 'success' })
        this.setData({
          amount: '',
          note: '',
          selectedTagIds: [],
          tagGroupKey: this.data.tagGroupKey + 1,
          dateStr: todayDateStr(),
          timeStr: nowTimeStr(),
        })
      })
      .catch(() => {})
      .then(() => {
        this.setData({ submitting: false })
      })
  },
})

const { request } = require('../../utils/request.js')
const { ensureHousehold } = require('../../utils/auth.js')

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function todayDateStr() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

Page({
  data: {
    txnId: 0,
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
    timeStr: '12:00',
    loading: true,
    submitting: false,
    deleting: false,
    tagGroupKey: 0,
  },

  onLoad(options) {
    const id = options.id ? Number(options.id) : 0
    this._txnId = id && !Number.isNaN(id) ? id : 0
    if (!this._txnId) {
      wx.showToast({ title: '无效流水', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 400)
    }
  },

  onShow() {
    if (!this._txnId) {
      return
    }
    ensureHousehold()
      .then(() => this.loadAll())
      .catch(() => this.setData({ loading: false }))
  },

  loadAll() {
    const txnId = this._txnId
    if (!txnId) {
      return
    }
    this.setData({ loading: true })
    Promise.all([
      request({ url: '/api/v1/ledgers', method: 'GET' }),
      request({ url: '/api/v1/fund-accounts', method: 'GET' }),
      request({ url: '/api/v1/tags', method: 'GET' }),
      request({ url: '/api/v1/transactions/' + txnId, method: 'GET' }),
    ])
      .then(([lg, fd, tg, txBody]) => {
        const ledgers = lg.data || []
        const funds = fd.data || []
        const fundPickerRange = ['（不计入余额）'].concat(funds.map((x) => x.name))
        const tags = tg.data || []
        const txn = txBody.data
        if (!txn) {
          this.setData({ loading: false })
          return
        }
        const ty = txn.type || 'EXPENSE'
        return request({
          url: '/api/v1/categories',
          method: 'GET',
          data: { type: ty },
        }).then((catBody) => {
          const categories = catBody.data || []
          let ledgerIdx = Math.max(0, ledgers.findIndex((l) => l.id === txn.ledgerId))
          let fundIdx = 0
          if (txn.fundAccountId != null) {
            const fi = funds.findIndex((f) => f.id === txn.fundAccountId)
            fundIdx = fi >= 0 ? fi + 1 : 0
          }
          const categoryIdx = Math.max(0, categories.findIndex((c) => c.id === txn.categoryId))
          const amount = txn.amount != null ? String(txn.amount) : ''
          const note = txn.note || ''
          const oc = txn.occurredAt || ''
          const parts = oc.split('T')
          let dateStr = todayDateStr()
          let timeStr = '12:00'
          if (parts[0]) {
            dateStr = parts[0]
          }
          if (parts[1]) {
            timeStr = parts[1].slice(0, 5)
          }
          const selectedTagIds = (txn.tagIds || []).map((x) => Number(x))
          this.setData({
            txnId,
            ledgers,
            funds,
            fundPickerRange,
            categories,
            tags,
            type: ty,
            ledgerIdx,
            fundIdx,
            categoryIdx,
            amount,
            note,
            dateStr,
            timeStr,
            selectedTagIds,
            tagGroupKey: this.data.tagGroupKey + 1,
            loading: false,
          })
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
      txnId,
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
    if (!txnId || submitting) {
      return
    }
    if (!ledgers.length || !categories.length) {
      wx.showToast({ title: '数据未就绪', icon: 'none' })
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
    request({
      url: '/api/v1/transactions/' + txnId,
      method: 'PUT',
      data: payload,
    })
      .then(() => {
        wx.showToast({ title: '已保存', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 350)
      })
      .catch(() => {})
      .then(() => {
        this.setData({ submitting: false })
      })
  },

  onDelete() {
    const id = this.data.txnId
    if (!id || this.data.deleting) {
      return
    }
    wx.showModal({
      title: '删除流水',
      content: '确定删除这条记录？',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        this.setData({ deleting: true })
        request({ url: '/api/v1/transactions/' + id, method: 'DELETE' })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' })
            setTimeout(() => wx.navigateBack(), 350)
          })
          .catch(() => {})
          .then(() => {
            this.setData({ deleting: false })
          })
      },
    })
  },
})

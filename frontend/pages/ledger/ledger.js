const { request } = require('../../utils/request.js')
const { ensureHousehold } = require('../../utils/auth.js')
const { formatMoney } = require('../../utils/formatMoney.js')

Page({
  data: {
    list: [],
    funds: [],
    loading: true,
    newLedgerName: '',
    savingLedger: false,
    newFundName: '',
    newFundBalance: '',
    savingFund: false,
  },

  onShow() {
    ensureHousehold()
      .then(() => this.load())
      .catch(() => {
        this.setData({ loading: false, list: [], funds: [] })
      })
  },

  load() {
    this.setData({ loading: true })
    Promise.all([
      request({ url: '/api/v1/ledgers', method: 'GET' }),
      request({ url: '/api/v1/fund-accounts', method: 'GET' }),
    ])
      .then(([lg, fd]) => {
        const funds = (fd.data || []).map((f) => ({
          ...f,
          balanceFmt: formatMoney(f.balance),
        }))
        this.setData({ list: lg.data || [], funds, loading: false })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  onNewLedgerInput(e) {
    this.setData({ newLedgerName: e.detail.value })
  },

  addLedger() {
    const name = (this.data.newLedgerName || '').trim()
    if (!name) {
      wx.showToast({ title: '请输入账本名称', icon: 'none' })
      return
    }
    if (this.data.savingLedger) {
      return
    }
    this.setData({ savingLedger: true })
    request({ url: '/api/v1/ledgers', method: 'POST', data: { name } })
      .then(() => {
        wx.showToast({ title: '已创建', icon: 'success' })
        this.setData({ newLedgerName: '' })
        return this.load()
      })
      .catch(() => {})
      .then(() => {
        this.setData({ savingLedger: false })
      })
  },

  openLedger(e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name || '账本流水'
    wx.navigateTo({
      url:
        '/pages/ledger-detail/ledger-detail?ledgerId=' +
        id +
        '&ledgerName=' +
        encodeURIComponent(name),
    })
  },

  openFund(e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name || '资金账户'
    wx.navigateTo({
      url:
        '/pages/ledger-detail/ledger-detail?fundId=' +
        id +
        '&fundName=' +
        encodeURIComponent(name),
    })
  },

  onNewFundName(e) {
    this.setData({ newFundName: e.detail.value })
  },

  onNewFundBalance(e) {
    this.setData({ newFundBalance: e.detail.value })
  },

  addFundAccount() {
    const name = (this.data.newFundName || '').trim()
    if (!name) {
      wx.showToast({ title: '请输入账户名称', icon: 'none' })
      return
    }
    let initialBalance = null
    const balRaw = (this.data.newFundBalance || '').trim()
    if (balRaw !== '') {
      const n = Number(balRaw)
      if (Number.isNaN(n)) {
        wx.showToast({ title: '期初余额请输入数字', icon: 'none' })
        return
      }
      initialBalance = n
    }
    if (this.data.savingFund) {
      return
    }
    this.setData({ savingFund: true })
    request({
      url: '/api/v1/fund-accounts',
      method: 'POST',
      data: { name, initialBalance },
    })
      .then(() => {
        wx.showToast({ title: '已创建账户', icon: 'success' })
        this.setData({ newFundName: '', newFundBalance: '' })
        return this.load()
      })
      .catch(() => {})
      .then(() => {
        this.setData({ savingFund: false })
      })
  },
})

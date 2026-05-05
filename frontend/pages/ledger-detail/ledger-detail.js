const { request } = require('../../utils/request.js')
const { ensureHousehold } = require('../../utils/auth.js')
const { formatMoney } = require('../../utils/formatMoney.js')

function formatDayTitle(iso) {
  if (!iso || iso === '未知') {
    return '未知日期'
  }
  const parts = iso.split('-')
  if (parts.length < 3) {
    return iso
  }
  const y = Number(parts[0])
  const m = Number(parts[1])
  const d = Number(parts[2])
  const dt = new Date(y, m - 1, d)
  const wk = ['日', '一', '二', '三', '四', '五', '六'][dt.getDay()]
  return m + '月' + d + '日 周' + wk
}

function groupRowsByDay(rows) {
  const groups = []
  let curKey = null
  let curItems = []
  rows.forEach((r) => {
    const iso = (r.occurredAt || '').split('T')[0] || ''
    const dayKey = iso || '未知'
    if (curKey !== dayKey) {
      if (curKey !== null) {
        groups.push({
          dayKey: curKey,
          dateLabel: formatDayTitle(curKey),
          items: curItems,
        })
      }
      curKey = dayKey
      curItems = []
    }
    curItems.push(r)
  })
  if (curKey !== null) {
    groups.push({
      dayKey: curKey,
      dateLabel: formatDayTitle(curKey),
      items: curItems,
    })
  }
  return groups
}

Page({
  data: {
    listMode: 'ledger',
    loading: true,
    dayGroups: [],
  },

  onLoad(options) {
    const fid = options.fundId ? Number(options.fundId) : 0
    const lid = options.ledgerId ? Number(options.ledgerId) : 0
    const rawName = options.fundName || options.ledgerName || ''
    const name = rawName ? decodeURIComponent(rawName) : ''
    if (name) {
      wx.setNavigationBarTitle({ title: name })
    }
    this._fundId = fid && !Number.isNaN(fid) ? fid : 0
    this._ledgerId = lid && !Number.isNaN(lid) ? lid : 0
    if (this._fundId) {
      this.setData({ listMode: 'fund' })
    } else {
      this.setData({ listMode: 'ledger' })
    }
  },

  onShow() {
    if (!this._ledgerId && !this._fundId) {
      this.setData({ loading: false, dayGroups: [] })
      return
    }
    ensureHousehold()
      .then(() => this.load())
      .catch(() => this.setData({ loading: false, dayGroups: [] }))
  },

  load() {
    this.setData({ loading: true })
    const txQuery = { limit: 500 }
    if (this._fundId) {
      txQuery.fundAccountId = this._fundId
    } else {
      txQuery.ledgerId = this._ledgerId
    }
    Promise.all([
      request({
        url: '/api/v1/transactions',
        method: 'GET',
        data: txQuery,
      }),
      request({ url: '/api/v1/categories', method: 'GET', data: { type: 'EXPENSE' } }),
      request({ url: '/api/v1/categories', method: 'GET', data: { type: 'INCOME' } }),
      request({ url: '/api/v1/fund-accounts', method: 'GET' }),
      request({ url: '/api/v1/tags', method: 'GET' }),
    ])
      .then(([txBody, ce, ci, fd, tg]) => {
        const list = txBody.data || []
        const catMap = {}
        ;(ce.data || []).concat(ci.data || []).forEach((c) => {
          catMap[c.id] = c.name
        })
        const fundMap = {}
        ;(fd.data || []).forEach((f) => {
          fundMap[f.id] = f.name
        })
        const tagMap = {}
        ;(tg.data || []).forEach((t) => {
          tagMap[t.id] = t.name
        })
        const rows = list.map((t) => {
          const tagIds = t.tagIds || []
          const tagNames = tagIds.map((id) => tagMap[id] || '#' + id).filter(Boolean)
          return {
            ...t,
            amountFmt: formatMoney(t.amount),
            categoryName: catMap[t.categoryId] || '—',
            fundName: t.fundAccountId != null ? fundMap[t.fundAccountId] || '—' : '未选账户',
            typeLabel: t.type === 'INCOME' ? '收入' : '支出',
            occurredShort: (t.occurredAt || '').replace('T', ' ').slice(0, 16),
            clockShort: (t.occurredAt || '').replace('T', ' ').slice(11, 16),
            tagLine: tagNames.length ? tagNames.join('、') : '',
          }
        })
        const dayGroups = groupRowsByDay(rows)
        this.setData({ dayGroups, loading: false })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  editTxn(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/txn-edit/txn-edit?id=' + id })
  },
})

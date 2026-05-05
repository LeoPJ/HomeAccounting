const { request } = require('../../utils/request.js')
const { ensureHousehold } = require('../../utils/auth.js')
const { formatMoney } = require('../../utils/formatMoney.js')

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

function currentMonthStr() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}

const PIE_COLORS = ['#4f46e5', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899']

Page({
  data: {
    templates: [],
    summaryRows: [],
    summaryDisplay: [],
    detailRows: [],
    detailDisplay: [],
    chartPieSlices: [],
    chartPieDisplay: [],
    runLimit: 0,
    detailLimit: 0,
    loading: true,
    reportMonth: currentMonthStr(),
    runningId: null,
    showPie: false,
  },

  onShow() {
    ensureHousehold()
      .then(() => this.loadTemplates())
      .catch(() => this.setData({ loading: false, templates: [] }))
  },

  loadTemplates() {
    this.setData({ loading: true })
    request({ url: '/api/v1/report-templates', method: 'GET' })
      .then((body) => {
        this.setData({ templates: body.data || [], loading: false })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  goNew() {
    wx.navigateTo({ url: '/pages/report-editor/report-editor' })
  },

  goEdit(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/report-editor/report-editor?id=' + id })
  },

  onMonthChange(e) {
    this.setData({ reportMonth: e.detail.value })
  },

  runTemplate(e) {
    const id = Number(e.currentTarget.dataset.id)
    const { reportMonth, runningId } = this.data
    if (runningId !== null) {
      return
    }
    const parts = reportMonth.split('-')
    const y = Number(parts[0])
    const m = Number(parts[1])
    const lastDay = new Date(y, m, 0).getDate()
    const start = `${y}-${pad(m)}-01T00:00:00`
    const end = `${y}-${pad(m)}-${pad(lastDay)}T23:59:59`
    this.setData({ runningId: id })
    request({
      url: '/api/v1/reports/run',
      method: 'POST',
      data: {
        templateId: id,
        extraFilters: [{ field: 'occurred_at', op: 'between', params: [start, end] }],
      },
    })
      .then((body) => {
        const data = body.data || {}
        const summaryRows = data.summaryRows || []
        const detailRows = data.detailRows || []
        const chartPieSlices = data.chartPieSlices || []
        const chartPieDisplay = chartPieSlices.map((s, i) => ({
          label: s.label,
          valueFmt: formatMoney(s.value),
          color: PIE_COLORS[i % PIE_COLORS.length],
        }))
        const summaryDisplay = summaryRows.map((row) => formatRowForDisplay(row))
        const detailDisplay = detailRows.map((row) => formatDetailRow(row))
        this.setData({
          summaryRows,
          summaryDisplay,
          detailRows,
          detailDisplay,
          chartPieSlices,
          chartPieDisplay,
          showPie: chartPieSlices.length > 0,
          runLimit: data.limit || 0,
          detailLimit: data.detailLimit || 0,
        })
        wx.showToast({ title: '已刷新', icon: 'success' })
        if (chartPieSlices.length > 0) {
          setTimeout(() => this.drawPie(chartPieSlices), 200)
        }
      })
      .catch(() => {})
      .then(() => {
        this.setData({ runningId: null })
      })
  },

  drawPie(slices) {
    const query = wx.createSelectorQuery().in(this)
    query
      .select('#pieCanvas')
      .fields({ node: true, size: true })
      .exec((res) => {
        if (!res || !res[0] || !res[0].node) {
          return
        }
        const canvas = res[0].node
        const ctx = canvas.getContext('2d')
        const w = res[0].width
        const h = res[0].height
        const dpr = wx.getSystemInfoSync().pixelRatio || 1
        canvas.width = w * dpr
        canvas.height = h * dpr
        ctx.scale(dpr, dpr)
        ctx.clearRect(0, 0, w, h)
        let total = 0
        slices.forEach((s) => {
          total += Math.abs(Number(s.value)) || 0
        })
        if (total <= 0) {
          return
        }
        const cx = w / 2
        const cy = h / 2
        const r = Math.min(cx, cy) - 8
        let angle = -Math.PI / 2
        slices.forEach((sl, i) => {
          const v = Math.abs(Number(sl.value)) || 0
          const slice = (v / total) * 2 * Math.PI
          ctx.beginPath()
          ctx.moveTo(cx, cy)
          ctx.arc(cx, cy, r, angle, angle + slice)
          ctx.closePath()
          ctx.fillStyle = PIE_COLORS[i % PIE_COLORS.length]
          ctx.fill()
          angle += slice
        })
      })
  },

  openTxnFromReport(e) {
    const id = e.currentTarget.dataset.id
    if (!id) {
      return
    }
    wx.navigateTo({ url: '/pages/txn-edit/txn-edit?id=' + id })
  },

  confirmDelete(e) {
    const id = e.currentTarget.dataset.id
    const name = e.currentTarget.dataset.name || ''
    wx.showModal({
      title: '删除模板',
      content: '确定删除「' + name + '」？',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        request({ url: '/api/v1/report-templates/' + id, method: 'DELETE' })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' })
            return this.loadTemplates()
          })
          .catch(() => {})
      },
    })
  },
})

function formatRowForDisplay(row) {
  const parts = []
  const keys = Object.keys(row)
  keys.forEach((k) => {
    let v = row[k]
    if (typeof v === 'number' || (v !== null && !Number.isNaN(Number(v)))) {
      const n = Number(v)
      if (!Number.isNaN(n) && String(k).length < 24) {
        v = formatMoney(n)
      }
    }
    parts.push(k + '：' + (v == null ? '—' : String(v)))
  })
  return parts.join(' · ')
}

function formatDetailRow(row) {
  const id = row.id
  const amt = row['金额']
  const amtFmt = amt != null ? formatMoney(amt) : '—'
  const cat = row['分类'] || '—'
  const time = row['发生时间'] || ''
  const shortTime = time.length > 16 ? time.slice(0, 16) : time
  return {
    raw: row,
    id,
    cat,
    amtFmt,
    shortTime,
    typeLabel: row['收支类型'] === '收入' ? '入' : '支',
    note: row['备注'] || '',
  }
}

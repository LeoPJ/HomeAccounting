const { request } = require('../../utils/request.js')
const { ensureHousehold } = require('../../utils/auth.js')
const rd = require('../../utils/reportDefinition.js')

function aliasOk(s) {
  return /^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test((s || '').trim())
}

Page({
  data: {
    loading: true,
    saving: false,
    schema: null,
    dimChoices: [],
    metricRowsPrepared: [],
    sortRowsPrepared: [],
    templateId: null,
    templateName: '',
    filterRows: [],
    filterRowsPrepared: [],
    selectedDims: [],
    metricRows: [{ alias: 'total', fn: 'sum', field: 'amount' }],
    sortRows: [],
    limitStr: '500',
    sortKeyOptions: [],
    showSchemaRef: false,
    schemaRefText: '',
  },

  onLoad(options) {
    const id = options.id ? Number(options.id) : null
    this._templateId = id && !Number.isNaN(id) ? id : null
    wx.setNavigationBarTitle({
      title: this._templateId ? '编辑模板' : '新建模板',
    })
    ensureHousehold()
      .then(() => this.bootstrap())
      .catch(() => this.setData({ loading: false }))
  },

  bootstrap() {
    this.setData({ loading: true })
    request({ url: '/api/v1/reports/schema', method: 'GET' })
      .then((sch) => {
        const schema = sch.data || {}
        const schemaRefText = JSON.stringify(schema, null, 2)
        if (this._templateId) {
          return request({
            url: '/api/v1/report-templates/' + this._templateId,
            method: 'GET',
          }).then((tpl) => ({ schema, schemaRefText, tpl: tpl.data }))
        }
        const filterRows = []
        const metricRows = [{ alias: 'total', fn: 'sum', field: 'amount' }]
        const lim = String((schema.limits && schema.limits.defaultLimit) || 500)
        this.setData({
          schema,
          schemaRefText,
          templateId: this._templateId,
          filterRows,
          metricRows,
          sortRows: [],
          limitStr: lim,
          selectedDims: [],
          loading: false,
        })
        this.refreshDerived(filterRows, metricRows, [])
        return null
      })
      .then((pack) => {
        if (!pack) {
          return
        }
        const { schema, schemaRefText, tpl } = pack
        const def = tpl.definition || {}
        const filterRows = rd.filterRowsFromDefinition(def, schema)
        const metricRows = rd.metricRowsFromDefinition(def)
        const sortRows = rd.sortRowsFromDefinition(def)
        const selectedDims = def.dimensions || []
        const lim =
          def.limit != null ? String(def.limit) : String((schema.limits && schema.limits.defaultLimit) || 500)
        this.setData({
          schema,
          schemaRefText,
          templateId: tpl.id,
          templateName: tpl.name || '',
          filterRows,
          metricRows,
          sortRows: sortRows.length ? sortRows : [],
          limitStr: lim,
          selectedDims,
          loading: false,
        })
        this.refreshDerived(filterRows, metricRows, selectedDims)
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  refreshDerived(filterRows, metricRows, selectedDims) {
    const schema = this.data.schema
    if (!schema) {
      return
    }
    const prepared = filterRows.map((r, index) => {
      const fi = Math.max(
        0,
        schema.filterFields.findIndex((f) => f.id === r.fieldId),
      )
      const ff = schema.filterFields[fi]
      const ops = ff ? ff.ops : []
      const opIdx = Math.max(0, ops.indexOf(r.op))
      return { ...r, _idx: index, _fieldIdx: fi, _ops: ops, _opIdx: opIdx }
    })
    const metricPrepared = metricRows.map((r, index) => {
      const mfs = schema.metricFunctions || []
      const fi = Math.max(0, mfs.findIndex((m) => m.fn === r.fn))
      const mf = mfs[fi]
      const needsField = !!(mf && mf.requiresMetricField)
      return { ...r, _idx: index, _fnIdx: fi, _needsField: needsField }
    })
    const aliases = metricRows.map((m) => (m.alias || '').trim()).filter(Boolean)
    const keys = []
    ;(selectedDims || []).forEach((id) => {
      keys.push({ label: '维度 · ' + id, value: id })
    })
    aliases.forEach((a) => {
      keys.push({ label: '指标 · ' + a, value: a })
    })
    const sortPrepared = (this.data.sortRows || []).map((s, index) => {
      let ki = keys.findIndex((k) => k.value === s.key)
      if (ki < 0) {
        ki = 0
      }
      const dirIdx = (s.dir || 'ASC').toUpperCase() === 'DESC' ? 1 : 0
      return { ...s, _idx: index, _keyIdx: ki, _dirIdx: dirIdx }
    })
    const dimChoices = (schema.dimensions || []).map((d) => ({
      id: d.id,
      label: d.label + (d.joinsTransactionTags ? ' · 含标签关联' : ''),
      checked: (selectedDims || []).some((x) => String(x) === String(d.id)),
    }))
    this.setData({
      filterRowsPrepared: prepared,
      metricRowsPrepared: metricPrepared,
      sortKeyOptions: keys,
      sortRowsPrepared: sortPrepared,
      dimChoices,
    })
  },

  onNameInput(e) {
    this.setData({ templateName: e.detail.value })
  },

  onLimitInput(e) {
    this.setData({ limitStr: e.detail.value })
  },

  toggleSchemaRef() {
    this.setData({ showSchemaRef: !this.data.showSchemaRef })
  },

  addFilterRow() {
    const schema = this.data.schema
    const rows = [...this.data.filterRows, rd.defaultFilterRow(schema)]
    this.setData({ filterRows: rows })
    this.refreshDerived(rows, this.data.metricRows, this.data.selectedDims)
  },

  removeFilterRow(e) {
    const index = Number(e.currentTarget.dataset.index)
    const rows = this.data.filterRows.filter((_, i) => i !== index)
    this.setData({ filterRows: rows })
    this.refreshDerived(rows, this.data.metricRows, this.data.selectedDims)
  },

  onFilterFieldChange(e) {
    const index = Number(e.currentTarget.dataset.index)
    const fi = Number(e.detail.value)
    const schema = this.data.schema
    const ff = schema.filterFields[fi]
    const rows = [...this.data.filterRows]
    rows[index] = {
      fieldId: ff.id,
      op: ff.ops[0],
      single: '',
      from: '',
      to: '',
      listStr: '',
    }
    this.setData({ filterRows: rows })
    this.refreshDerived(rows, this.data.metricRows, this.data.selectedDims)
  },

  onFilterOpChange(e) {
    const index = Number(e.currentTarget.dataset.index)
    const oi = Number(e.detail.value)
    const row = this.data.filterRowsPrepared[index]
    const op = row._ops[oi]
    const rows = [...this.data.filterRows]
    rows[index] = { ...rows[index], op }
    this.setData({ filterRows: rows })
    this.refreshDerived(rows, this.data.metricRows, this.data.selectedDims)
  },

  onFilterSingle(e) {
    const index = Number(e.currentTarget.dataset.index)
    const rows = [...this.data.filterRows]
    rows[index] = { ...rows[index], single: e.detail.value }
    this.setData({ filterRows: rows })
    this.refreshDerived(rows, this.data.metricRows, this.data.selectedDims)
  },

  onFilterFrom(e) {
    const index = Number(e.currentTarget.dataset.index)
    const rows = [...this.data.filterRows]
    rows[index] = { ...rows[index], from: e.detail.value }
    this.setData({ filterRows: rows })
    this.refreshDerived(rows, this.data.metricRows, this.data.selectedDims)
  },

  onFilterTo(e) {
    const index = Number(e.currentTarget.dataset.index)
    const rows = [...this.data.filterRows]
    rows[index] = { ...rows[index], to: e.detail.value }
    this.setData({ filterRows: rows })
    this.refreshDerived(rows, this.data.metricRows, this.data.selectedDims)
  },

  onFilterList(e) {
    const index = Number(e.currentTarget.dataset.index)
    const rows = [...this.data.filterRows]
    rows[index] = { ...rows[index], listStr: e.detail.value }
    this.setData({ filterRows: rows })
    this.refreshDerived(rows, this.data.metricRows, this.data.selectedDims)
  },

  onDimsChange(e) {
    const selectedDims = e.detail.value || []
    this.setData({ selectedDims })
    this.refreshDerived(this.data.filterRows, this.data.metricRows, selectedDims)
  },

  addMetricRow() {
    const schema = this.data.schema
    const mf = (schema.metricFunctions && schema.metricFunctions[0]) || { fn: 'sum' }
    const rows = [...this.data.metricRows, { alias: 'cnt', fn: mf.fn, field: 'amount' }]
    this.setData({ metricRows: rows })
    this.refreshDerived(this.data.filterRows, rows, this.data.selectedDims)
  },

  removeMetricRow(e) {
    const index = Number(e.currentTarget.dataset.index)
    let rows = this.data.metricRows.filter((_, i) => i !== index)
    if (!rows.length) {
      rows = [{ alias: 'total', fn: 'sum', field: 'amount' }]
    }
    this.setData({ metricRows: rows })
    this.refreshDerived(this.data.filterRows, rows, this.data.selectedDims)
  },

  onMetricAlias(e) {
    const index = Number(e.currentTarget.dataset.index)
    const rows = [...this.data.metricRows]
    rows[index] = { ...rows[index], alias: e.detail.value }
    this.setData({ metricRows: rows })
    this.refreshDerived(this.data.filterRows, rows, this.data.selectedDims)
  },

  onMetricFnChange(e) {
    const index = Number(e.currentTarget.dataset.index)
    const fi = Number(e.detail.value)
    const schema = this.data.schema
    const mf = schema.metricFunctions[fi]
    const rows = [...this.data.metricRows]
    rows[index] = {
      ...rows[index],
      fn: mf.fn,
      field: mf.requiresMetricField ? 'amount' : '',
    }
    this.setData({ metricRows: rows })
    this.refreshDerived(this.data.filterRows, rows, this.data.selectedDims)
  },

  addSortRow() {
    const opts = this.data.sortKeyOptions
    if (!opts.length) {
      wx.showToast({ title: '先勾选维度或填写指标别名', icon: 'none' })
      return
    }
    const key = opts[0].value
    const rows = [...this.data.sortRows, { key, dir: 'ASC' }]
    this.setData({ sortRows: rows })
    this.refreshDerived(this.data.filterRows, this.data.metricRows, this.data.selectedDims)
  },

  removeSortRow(e) {
    const index = Number(e.currentTarget.dataset.index)
    const rows = this.data.sortRows.filter((_, i) => i !== index)
    this.setData({ sortRows: rows })
    this.refreshDerived(this.data.filterRows, this.data.metricRows, this.data.selectedDims)
  },

  onSortKeyChange(e) {
    const index = Number(e.currentTarget.dataset.index)
    const ki = Number(e.detail.value)
    const key = (this.data.sortKeyOptions[ki] || {}).value || ''
    const rows = [...this.data.sortRows]
    rows[index] = { ...rows[index], key }
    this.setData({ sortRows: rows })
    this.refreshDerived(this.data.filterRows, this.data.metricRows, this.data.selectedDims)
  },

  onSortDirChange(e) {
    const index = Number(e.currentTarget.dataset.index)
    const di = Number(e.detail.value)
    const dir = di === 1 ? 'DESC' : 'ASC'
    const rows = [...this.data.sortRows]
    rows[index] = { ...rows[index], dir }
    this.setData({ sortRows: rows })
    this.refreshDerived(this.data.filterRows, this.data.metricRows, this.data.selectedDims)
  },

  validate() {
    const name = (this.data.templateName || '').trim()
    if (!name) {
      wx.showToast({ title: '请填写模板名称', icon: 'none' })
      return false
    }
    const schema = this.data.schema
    for (let i = 0; i < this.data.metricRows.length; i++) {
      const m = this.data.metricRows[i]
      if (!aliasOk(m.alias)) {
        wx.showToast({ title: '指标别名格式不正确', icon: 'none' })
        return false
      }
      const meta = schema.metricFunctions.find((x) => x.fn === m.fn)
      if (meta && meta.requiresMetricField && (m.field || '') !== 'amount') {
        wx.showToast({ title: '聚合类指标 field 须为 amount', icon: 'none' })
        return false
      }
    }
    for (let i = 0; i < this.data.filterRows.length; i++) {
      const row = this.data.filterRows[i]
      if (!rd.isFilterRowComplete(row, schema)) {
        wx.showToast({ title: '筛选条件 ' + (i + 1) + ' 未填写完整', icon: 'none' })
        return false
      }
    }
    return true
  },

  onSave() {
    if (!this.validate() || this.data.saving) {
      return
    }
    const schema = this.data.schema
    const definition = rd.buildDefinition(
      {
        filterRows: this.data.filterRows,
        selectedDims: this.data.selectedDims,
        metricRows: this.data.metricRows,
        sortRows: this.data.sortRows,
        limit: this.data.limitStr,
      },
      schema,
    )
    const body = {
      name: (this.data.templateName || '').trim(),
      definition,
    }
    this.setData({ saving: true })
    const req = this.data.templateId
      ? request({
          url: '/api/v1/report-templates/' + this.data.templateId,
          method: 'PUT',
          data: body,
        })
      : request({
          url: '/api/v1/report-templates',
          method: 'POST',
          data: body,
        })
    req
      .then(() => {
        wx.showToast({ title: '已保存', icon: 'success' })
        setTimeout(() => wx.navigateBack(), 400)
      })
      .catch(() => {})
      .then(() => {
        this.setData({ saving: false })
      })
  },

  onDelete() {
    if (!this.data.templateId) {
      return
    }
    wx.showModal({
      title: '删除模板',
      content: '确定删除该报表模板？',
      success: (res) => {
        if (!res.confirm) {
          return
        }
        request({
          url: '/api/v1/report-templates/' + this.data.templateId,
          method: 'DELETE',
        })
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' })
            setTimeout(() => wx.navigateBack(), 400)
          })
          .catch(() => {})
      },
    })
  },
})

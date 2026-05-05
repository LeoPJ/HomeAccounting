/**
 * 与后端 ReportDefinitionPayload / FilterClause 对齐，供报表模板编辑器组装 JSON。
 */

/**
 * 该行筛选是否已填写完整，可提交后端校验。
 */
function isFilterRowComplete(row, schema) {
  const ff = fieldMeta(schema, row.fieldId)
  if (!ff) {
    return false
  }
  const vt = ff.valueType
  const op = row.op
  const params = parseParamsFromRow(row, vt)

  if (op === 'is_null' || op === 'is_not_null') {
    return true
  }
  if (op === 'between') {
    const a = (row.from || '').trim()
    const b = (row.to || '').trim()
    return a !== '' && b !== ''
  }
  if (op === 'in') {
    return params.length > 0
  }
  if (['eq', 'ne', 'gt', 'gte', 'lt', 'lte'].includes(op)) {
    if (params.length === 0) {
      return false
    }
    const v = params[0]
    if (v === '' || v === null || v === undefined) {
      return false
    }
    if (vt === 'long' || vt === 'tag') {
      return typeof v === 'number' && !Number.isNaN(v)
    }
    if (ff.id === 'type') {
      const s = String(v).trim()
      return s === 'EXPENSE' || s === 'INCOME'
    }
    return String(v).trim() !== ''
  }
  return false
}

function parseParamsFromRow(row, valueType) {
  const op = row.op
  if (op === 'is_null' || op === 'is_not_null') {
    return []
  }
  if (op === 'between') {
    return [(row.from || '').trim(), (row.to || '').trim()]
  }
  if (op === 'in') {
    const parts = (row.listStr || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean)
    if (valueType === 'enum') {
      return parts
    }
    return parts.map((p) => Number(p)).filter((n) => !Number.isNaN(n))
  }
  const s = (row.single || '').trim()
  if (valueType === 'long' || valueType === 'tag') {
    const n = Number(s)
    return Number.isNaN(n) ? [] : [n]
  }
  return [s]
}

function fieldMeta(schema, fieldId) {
  const list = (schema && schema.filterFields) || []
  return list.find((f) => f.id === fieldId) || null
}

function buildFilterClauses(filterRows, schema) {
  return filterRows.map((row) => {
    const ff = fieldMeta(schema, row.fieldId)
    const vt = ff ? ff.valueType : ''
    const params = parseParamsFromRow(row, vt)
    return { field: row.fieldId, op: row.op, params }
  })
}

function defaultFilterRow(schema) {
  const fields = schema.filterFields || []
  const ff =
    fields.find((f) => f.id === 'fund_account_id') || fields[0]
  if (!ff) {
    return { fieldId: 'occurred_at', op: 'between', single: '', from: '', to: '', listStr: '' }
  }
  const op =
    ff.id === 'fund_account_id' && ff.ops && ff.ops.includes('is_null')
      ? 'is_null'
      : ff.ops[0]
  return {
    fieldId: ff.id,
    op,
    single: '',
    from: '',
    to: '',
    listStr: '',
  }
}

function filterRowsFromDefinition(def, schema) {
  const list = (def && def.filters) || []
  if (!list.length) {
    return []
  }
  return list.map((f) => {
    const row = {
      fieldId: f.field,
      op: f.op,
      single: '',
      from: '',
      to: '',
      listStr: '',
    }
    const p = f.params || []
    if (f.op === 'between') {
      row.from = String(p[0] != null ? p[0] : '')
      row.to = String(p[1] != null ? p[1] : '')
    } else if (f.op === 'in') {
      row.listStr = p.join(',')
    } else if (f.op !== 'is_null' && f.op !== 'is_not_null') {
      row.single = String(p[0] != null ? p[0] : '')
    }
    return row
  })
}

function metricRowsFromDefinition(def) {
  const list = (def && def.metrics) || []
  if (!list.length) {
    return [{ alias: 'total', fn: 'sum', field: 'amount' }]
  }
  return list.map((m) => {
    const fn = m.fn || 'sum'
    const needs = ['sum', 'min', 'max', 'avg'].includes(fn)
    return {
      alias: m.alias,
      fn,
      field: needs ? (m.field || 'amount') : '',
    }
  })
}

function sortRowsFromDefinition(def) {
  return (def && def.sort) ? def.sort.map((s) => ({ key: s.key, dir: s.dir || 'ASC' })) : []
}

function buildDefinition(form, schema) {
  const filters = buildFilterClauses(form.filterRows, schema)
  const dimensions = form.selectedDims || []
  const metrics = (form.metricRows || []).map((m) => {
    const meta = (schema.metricFunctions || []).find((x) => x.fn === m.fn)
    const needs = meta && meta.requiresMetricField
    const alias = (m.alias || '').trim()
    if (needs) {
      return { alias, fn: m.fn, field: 'amount' }
    }
    return { alias, fn: m.fn }
  })
  const sort = (form.sortRows || [])
    .filter((s) => (s.key || '').trim())
    .map((s) => ({ key: s.key.trim(), dir: (s.dir || 'ASC').toUpperCase() }))
  let limit = Number(form.limit)
  if (Number.isNaN(limit)) {
    limit = (schema.limits && schema.limits.defaultLimit) || 500
  }
  return {
    filters,
    dimensions,
    metrics,
    sort,
    limit,
  }
}

module.exports = {
  buildDefinition,
  defaultFilterRow,
  filterRowsFromDefinition,
  metricRowsFromDefinition,
  sortRowsFromDefinition,
  fieldMeta,
  parseParamsFromRow,
  isFilterRowComplete,
}

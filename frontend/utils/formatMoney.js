/**
 * 会计常用金额展示：千分位 + 两位小数（zh-CN）
 * @param {number|string|null|undefined} n
 * @returns {string}
 */
function formatMoney(n) {
  if (n === null || n === undefined || n === '') {
    return '—'
  }
  const num = typeof n === 'string' ? Number(n) : n
  if (Number.isNaN(num)) {
    return '—'
  }
  return num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

module.exports = {
  formatMoney,
}

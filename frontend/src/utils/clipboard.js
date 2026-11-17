/**
 * 复制文本到剪贴板（公网 HTTP 环境可用）。
 *
 * 背景：navigator.clipboard 只在安全上下文（HTTPS / localhost）暴露，
 * 站点是公网 IP 纯 HTTP 部署时浏览器直接禁用该 API，所有裸调用都会失败。
 * 这里做两级降级：优先 Clipboard API，失败后用隐藏 textarea +
 * document.execCommand('copy')（Chrome / Edge / Firefox / Safari 均支持）。
 */
export async function copyToClipboard(text) {
  const value = text == null ? '' : String(text)
  if (!value.trim()) return false

  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(value)
      return true
    } catch {
      /* 权限被拒或非安全上下文 → 走降级 */
    }
  }

  try {
    const ta = document.createElement('textarea')
    ta.value = value
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.top = '-9999px'
    ta.style.left = '-9999px'
    document.body.appendChild(ta)
    ta.select()
    ta.setSelectionRange(0, value.length)
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}

/**
 * Simple markdown-to-HTML renderer
 * Lightweight alternative to the full `marked` library.
 * Handles common markdown patterns used in LLM responses.
 */

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function renderInline(text: string): string {
  // Code (inline) - must be first to avoid processing inside tags
  let result = text.replace(/`([^`]+)`/g, '<code>$1</code>')

  // Bold
  result = result.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')

  // Italic
  result = result.replace(/\*([^*]+)\*/g, '<em>$1</em>')

  // Strikethrough
  result = result.replace(/~~([^~]+)~~/g, '<del>$1</del>')

  // Links
  result = result.replace(
    /\[([^\]]+)\]\(([^)]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener">$1</a>',
  )

  return result
}

export function marked(text: string): string {
  if (!text) return ''

  const lines = text.split('\n')
  const html: string[] = []
  let inCodeBlock = false
  let codeBuffer: string[] = []
  let codeLang = ''
  let inList = false
  let listType: 'ul' | 'ol' | null = null
  let inTable = false

  function flushCodeBlock() {
    if (codeBuffer.length > 0) {
      const lang = codeLang ? ` class="language-${escapeHtml(codeLang)}"` : ''
      html.push(`<pre><code${lang}>${codeBuffer.join('\n')}</code></pre>`)
      codeBuffer = []
      codeLang = ''
    }
  }

  function flushList() {
    if (inList) {
      html.push(listType === 'ol' ? '</ol>' : '</ul>')
      inList = false
      listType = null
    }
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]

    // Code block fence
    if (/^```/.test(line)) {
      if (inCodeBlock) {
        flushCodeBlock()
        inCodeBlock = false
      } else {
        flushCodeBlock()
        flushList()
        inCodeBlock = true
        codeLang = line.slice(3).trim()
      }
      continue
    }

    if (inCodeBlock) {
      // Escape code content
      codeBuffer.push(escapeHtml(line))
      continue
    }

    const trimmed = line.trim()

    // Empty line
    if (!trimmed) {
      flushList()
      if (!inTable) {
        html.push('')
      }
      continue
    }

    // Horizontal rule
    if (/^[-*_]{3,}$/.test(trimmed)) {
      flushList()
      html.push('<hr>')
      continue
    }

    // Headings
    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/)
    if (headingMatch) {
      flushList()
      const level = headingMatch[1].length
      html.push(`<h${level}>${renderInline(headingMatch[2])}</h${level}>`)
      continue
    }

    // Unordered list
    const ulMatch = trimmed.match(/^[-*+]\s+(.+)$/)
    if (ulMatch) {
      if (!inList || listType !== 'ul') {
        flushList()
        inList = true
        listType = 'ul'
        html.push('<ul>')
      }
      html.push(`<li>${renderInline(ulMatch[1])}</li>`)
      continue
    }

    // Ordered list
    const olMatch = trimmed.match(/^\d+\.\s+(.+)$/)
    if (olMatch) {
      if (!inList || listType !== 'ol') {
        flushList()
        inList = true
        listType = 'ol'
        html.push('<ol>')
      }
      html.push(`<li>${renderInline(olMatch[1])}</li>`)
      continue
    }

    // Table row
    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      const cells = trimmed
        .split('|')
        .slice(1, -1)
        .map((c) => c.trim())

      // Separator row
      if (/^[-:| ]+$/.test(cells.join(''))) {
        continue
      }

      if (!inTable) {
        inTable = true
        html.push('<table>')
        html.push('<thead><tr>' + cells.map((c) => `<th>${renderInline(c)}</th>`).join('') + '</tr></thead>')
        html.push('<tbody>')
      } else {
        html.push('<tr>' + cells.map((c) => `<td>${renderInline(c)}</td>`).join('') + '</tr>')
      }
      continue
    }

    // Blockquote
    const bqMatch = trimmed.match(/^>\s+(.+)$/)
    if (bqMatch) {
      flushList()
      html.push(`<blockquote>${renderInline(bqMatch[1])}</blockquote>`)
      continue
    }

    // Regular paragraph
    flushList()
    const rendered = renderInline(trimmed)
    html.push(`<p>${rendered}</p>`)
  }

  // Close any open blocks
  if (inCodeBlock) {
    flushCodeBlock()
  }
  flushList()
  if (inTable) {
    html.push('</tbody></table>')
  }

  return html.join('\n')
}

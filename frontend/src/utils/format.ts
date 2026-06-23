/**
 * Format file size in human-readable form
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  const size = (bytes / Math.pow(k, i)).toFixed(i > 0 ? 1 : 0)
  return `${size} ${units[i]}`
}

/**
 * Format date string to a friendlier format
 */
export function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  return dateStr.replace('T', ' ')
}

/**
 * Truncate a string to maxLen chars, adding ellipsis if truncated
 */
export function truncate(str: string, maxLen = 50): string {
  if (str.length <= maxLen) return str
  return str.slice(0, maxLen) + '...'
}

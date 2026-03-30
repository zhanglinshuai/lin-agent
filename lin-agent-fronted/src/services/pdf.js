import axios from 'axios'

export async function downloadPdfFile({ fileName = '', title = '', subtitle = '', content = '' }) {
  const safeFileName = String(fileName || 'export.pdf')
  const res = await axios.post('/api/ai/pdf/export', {
    fileName: safeFileName,
    title: String(title || ''),
    subtitle: String(subtitle || ''),
    content: String(content || ''),
  }, {
    responseType: 'blob',
  })
  const blob = new Blob([res.data], { type: 'application/pdf' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = safeFileName.toLowerCase().endsWith('.pdf') ? safeFileName : `${safeFileName}.pdf`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

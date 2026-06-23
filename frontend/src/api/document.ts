import { api } from './request'

export interface DocumentResponse {
  id: number
  kbId: number
  fileName: string
  fileType: string
  fileSize: number
  chunkCount: number | null
  status: number // 0=PROCESSING, 1=COMPLETED, 2=FAILED
  errorMsg: string | null
  createTime: string
  updateTime: string
}

export interface RetrievedChunk {
  docId: number
  chunkIndex: number
  content: string
  sourceText: string
  score: number
  fileName: string
}

/** Upload a document */
export function uploadDocumentApi(kbId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('kbId', String(kbId))
  return api.post<number>('/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** List documents in a knowledge base */
export function listDocumentsApi(kbId: number) {
  return api.get<DocumentResponse[]>('/document/list', { params: { kbId } })
}

/** Delete a document */
export function deleteDocumentApi(id: number) {
  return api.delete<void>(`/document/${id}`)
}

/** Search test */
export function searchTestApi(kbId: number, query: string, topK = 5) {
  return api.get<RetrievedChunk[]>('/search/test', {
    params: { kbId, query, topK },
  })
}

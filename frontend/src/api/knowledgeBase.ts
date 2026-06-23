import { api } from './request'

export interface KnowledgeBaseResponse {
  id: number
  name: string
  description: string
  collectionName: string
  createTime: string
  updateTime: string
}

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string
}

/** List all knowledge bases */
export function listKnowledgeBasesApi() {
  return api.get<KnowledgeBaseResponse[]>('/kb/list')
}

/** Create a knowledge base */
export function createKnowledgeBaseApi(data: CreateKnowledgeBaseRequest) {
  return api.post<KnowledgeBaseResponse>('/kb/create', data)
}

/** Delete a knowledge base */
export function deleteKnowledgeBaseApi(id: number) {
  return api.delete<void>(`/kb/${id}`)
}

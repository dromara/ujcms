import axios from '@/utils/request';

export const uploadWebFileTemplateUrl = `${import.meta.env.VITE_BASE_API}/backend/ext/web-file-template/upload`;
export const uploadZipWebFileTemplateUrl = `${import.meta.env.VITE_BASE_API}/backend/ext/web-file-template/upload-zip`;
export const queryWebFileTemplateList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/web-file-template', { params })).data;
export const queryWebFileTemplate = async (id: string): Promise<any> => (await axios.get('/backend/ext/web-file-template/show', { params: { id } })).data;
export const downloadZipWebFileTemplate = async (dir: string, names: string[]): Promise<any> =>
  (await axios.post('/backend/ext/web-file-template/download-zip', { dir, names }, { responseType: 'blob' })).data;
export const createWebFileTemplate = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-template', data)).data;
export const mkdirWebFileTemplate = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-template/mkdir', data)).data;
export const updateWebFileTemplate = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-template?_method=put', data)).data;
export const renameWebFileTemplate = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-template/rename?_method=put', data)).data;
export const copyWebFileTemplate = async (dir: string, names: string[], destDir: string): Promise<any> =>
  (await axios.post('/backend/ext/web-file-template/copy', { dir, names, destDir })).data;
export const moveWebFileTemplate = async (dir: string, names: string[], destDir: string): Promise<any> =>
  (await axios.post('/backend/ext/web-file-template/move', { dir, names, destDir })).data;
export const deleteWebFileTemplate = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/web-file-template?_method=delete', data)).data;

export const uploadWebFileUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/ext/web-file-upload/upload`;
export const uploadZipWebFileUploadUrl = `${import.meta.env.VITE_BASE_API}/backend/ext/web-file-upload/upload-zip`;
export const queryWebFileUploadList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/web-file-upload', { params })).data;
export const queryWebFileUpload = async (id: string): Promise<any> => (await axios.get('/backend/ext/web-file-upload/show', { params: { id } })).data;
export const downloadZipWebFileUpload = async (dir: string, names: string[]): Promise<any> =>
  (await axios.post('/backend/ext/web-file-upload/download-zip', { dir, names }, { responseType: 'blob' })).data;
export const createWebFileUpload = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-upload', data)).data;
export const mkdirWebFileUpload = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-upload/mkdir', data)).data;
export const updateWebFileUpload = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-upload?_method=put', data)).data;
export const renameWebFileUpload = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-upload/rename?_method=put', data)).data;
export const copyWebFileUpload = async (dir: string, names: string[], destDir: string): Promise<any> =>
  (await axios.post('/backend/ext/web-file-upload/copy', { dir, names, destDir })).data;
export const moveWebFileUpload = async (dir: string, names: string[], destDir: string): Promise<any> =>
  (await axios.post('/backend/ext/web-file-upload/move', { dir, names, destDir })).data;
export const deleteWebFileUpload = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/web-file-upload?_method=delete', data)).data;

export const uploadWebFileHtmlUrl = `${import.meta.env.VITE_BASE_API}/backend/ext/web-file-html/upload`;
export const uploadZipWebFileHtmlUrl = `${import.meta.env.VITE_BASE_API}/backend/ext/web-file-html/upload-zip`;
export const queryWebFileHtmlList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/web-file-html', { params })).data;
export const queryWebFileHtml = async (id: string): Promise<any> => (await axios.get('/backend/ext/web-file-html/show', { params: { id } })).data;
export const downloadZipWebFileHtml = async (dir: string, names: string[]): Promise<any> =>
  (await axios.post('/backend/ext/web-file-html/download-zip', { dir, names }, { responseType: 'blob' })).data;
export const createWebFileHtml = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-html', data)).data;
export const mkdirWebFileHtml = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-html/mkdir', data)).data;
export const updateWebFileHtml = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-html?_method=put', data)).data;
export const renameWebFileHtml = async (data: Record<string, any>): Promise<any> => (await axios.post('/backend/ext/web-file-html/rename?_method=put', data)).data;
export const copyWebFileHtml = async (dir: string, names: string[], destDir: string): Promise<any> =>
  (await axios.post('/backend/ext/web-file-html/copy', { dir, names, destDir })).data;
export const moveWebFileHtml = async (dir: string, names: string[], destDir: string): Promise<any> =>
  (await axios.post('/backend/ext/web-file-html/move', { dir, names, destDir })).data;
export const deleteWebFileHtml = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/web-file-html?_method=delete', data)).data;

export const queryBackupDatabaseList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/backup-database', { params })).data;
export const queryBackupDatabase = async (id: string): Promise<any> => (await axios.get(`/backend/ext/backup-database/${id}`)).data;
export const backupBackupDatabase = async (): Promise<any> => (await axios.post('/backend/ext/backup-database')).data;
export const restoreBackupDatabase = async (name: string): Promise<any> => (await axios.post('/backend/ext/backup-database?_method=put', { name })).data;
export const deleteBackupDatabase = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/backup-database?_method=delete', data)).data;

export const queryBackupTemplatesList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/backup-templates', { params })).data;
export const queryBackupTemplates = async (id: string): Promise<any> => (await axios.get(`/backend/ext/backup-templates/${id}`)).data;
export const backupBackupTemplates = async (): Promise<any> => (await axios.post('/backend/ext/backup-templates')).data;
export const restoreBackupTemplates = async (name: string): Promise<any> => (await axios.post('/backend/ext/backup-templates?_method=put', { name })).data;
export const deleteBackupTemplates = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/backup-templates?_method=delete', data)).data;

export const queryBackupUploadsList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/backup-uploads', { params })).data;
export const queryBackupUploads = async (id: string): Promise<any> => (await axios.get(`/backend/ext/backup-uploads/${id}`)).data;
export const backupBackupUploads = async (): Promise<any> => (await axios.post('/backend/ext/backup-uploads')).data;
export const restoreBackupUploads = async (name: string): Promise<any> => (await axios.post('/backend/ext/backup-uploads?_method=put', { name })).data;
export const deleteBackupUploads = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/backup-uploads?_method=delete', data)).data;

export const queryIncrementalUploadsList = async (params?: Record<string, any>): Promise<any> => (await axios.get('/backend/ext/incremental-uploads', { params })).data;
export const queryIncrementalUploads = async (id: string): Promise<any> => (await axios.get(`/backend/ext/incremental-uploads/${id}`)).data;
export const backupIncrementalUploads = async (): Promise<any> => (await axios.post('/backend/ext/incremental-uploads')).data;
export const restoreIncrementalUploads = async (name: string): Promise<any> => (await axios.post('/backend/ext/incremental-uploads?_method=put', { name })).data;
export const mergeIncrementalUploads = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/incremental-uploads/merge', data)).data;
export const deleteIncrementalUploads = async (data: string[]): Promise<any> => (await axios.post('/backend/ext/incremental-uploads?_method=delete', data)).data;

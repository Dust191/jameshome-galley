import { useState } from 'react'
import { Alert, App as AntApp, Modal, Table, Typography, Upload } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../../lib/api'

type RowError = { line: number; field: string; message: string; raw: string }
type ImportResult = { total: number; created: number; errors: RowError[] }

/**
 * 物料批量导入弹窗。
 *
 * <p>后端是"整批校验、全部通过才写库"，所以这里不做逐行过滤——把文件原样交上去，
 * 由服务端返回权威结果：成功多少行、哪一行哪一列错。
 */
export default function MaterialImportModal({
  open,
  onClose,
}: {
  open: boolean
  onClose: () => void
}) {
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const reset = () => {
    setFile(null)
    setResult(null)
    setError(null)
  }

  const upload = useMutation({
    mutationFn: (selected: File) => {
      const data = new FormData()
      data.append('file', selected)
      return api.upload<ImportResult>('/api/v1/imports/materials', data)
    },
    onSuccess: (data) => {
      setResult(data)
      if (data.created > 0) {
        message.success(`已导入 ${data.created} 条`)
        queryClient.invalidateQueries({ queryKey: ['materials'] })
        queryClient.invalidateQueries({ queryKey: ['stats'] })
      }
    },
    onError: (e) => {
      setError(e instanceof ApiError ? e.message : '导入失败，请稍后重试')
    },
  })

  return (
    <Modal
      title="导入物料"
      open={open}
      width={640}
      okText="开始导入"
      cancelText="关闭"
      okButtonProps={{ disabled: !file, loading: upload.isPending }}
      onOk={() => file && upload.mutate(file)}
      onCancel={onClose}
      afterClose={reset}
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        message="整批校验：任意一行有错，整批都不会写入"
        description="CSV 表头：自编码, 物料名, 分类, 单位, 规格, 安全库存, 保质期(天), 当前结存, 状态, 备注"
        style={{ marginBottom: 12 }}
      />

      <Upload.Dragger
        accept=".csv,text/csv"
        maxCount={1}
        beforeUpload={(selected) => {
          setFile(selected)
          setResult(null)
          setError(null)
          return false
        }}
        onRemove={() => setFile(null)}
        fileList={file ? [{ uid: '1', name: file.name }] : []}
      >
        <p className="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p className="ant-upload-text">点击或拖拽 CSV 文件到这里</p>
        <p className="ant-upload-hint">UTF-8 编码；单次最多 5000 行</p>
      </Upload.Dragger>

      {error && <Alert type="error" showIcon message={error} style={{ marginTop: 12 }} />}

      {result && (
        <div style={{ marginTop: 12 }}>
          <Alert
            type={result.errors.length === 0 ? 'success' : 'warning'}
            showIcon
            message={`共 ${result.total} 行，成功 ${result.created} 行，失败 ${result.errors.length} 行`}
          />
          {result.errors.length > 0 && (
            <Table<RowError>
              size="small"
              rowKey={(row) => `${row.line}-${row.field}`}
              dataSource={result.errors}
              pagination={false}
              style={{ marginTop: 8 }}
              columns={[
                { title: '行号', dataIndex: 'line', width: 70 },
                { title: '字段', dataIndex: 'field', width: 100 },
                { title: '原因', dataIndex: 'message' },
              ]}
            />
          )}
          <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginTop: 8 }}>
            行号对应 Excel 里的物理行（表头为第 1 行），照着改完重新导入即可。
          </Typography.Paragraph>
        </div>
      )}
    </Modal>
  )
}

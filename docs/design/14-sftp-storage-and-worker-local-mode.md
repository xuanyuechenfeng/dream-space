# SFTP 存储替换与 Worker 本地落盘设计

## 1. 背景与目标

当前 API 和 Worker 通过 `ObjectStorage` 抽象访问本地文件系统或远程对象存储。历史实现使用 S3/MinIO；Worker 的真实图片模型已可用，但旧启动校验强制要求 `OBJECT_STORAGE_MODE=s3`，导致本地开发环境无法启动。

本次变更目标：

1. API 与 Worker 删除 S3/MinIO 适配和配置，远程文件存储统一改为 SFTP。
2. Worker 在 `local` 模式下仍可调用真实规划模型、真实图片模型、真实评估模型和审核模型，生成主图与缩略图写入本地目录。
3. 保持任务、结果、审核、额度、SSE 和结果下载 HTTP 契约不变。
4. `application.yml` 中现有测试 API key 按现状保留，不在本次变更中轮换或撤销；生产部署仍应使用环境变量覆盖。

非目标：不改变 PostgreSQL/Redis、任务状态机、图片模型请求格式、WebP 编码规则、数据库任务结果字段语义，也不把 SFTP 路径直接暴露给浏览器。

## 2. 当前问题与约束

- 旧版 `SharedPersistenceConfiguration` 条件化创建 S3 客户端和适配器。
- 旧版 `GenerationWorkerConfiguration.requireStorageConfiguration()` 在所有真实模型 Bean 创建时强制 `storage.isS3()`，因此默认 `local` 会在 Spring 启动阶段失败。
- `GenerationOutputPipeline` 已经只依赖 `ObjectStorage.put/delete`，Worker 输出链不需要感知具体存储协议。
- API 的结果与参考图接口均通过服务端代理二进制数据；S3 的签名 URL 不是当前前端必须依赖的能力。
- `GenerationResult.objectKey`、`thumbnailObjectKey` 和 `ReferenceUpload.objectKey` 是逻辑存储路径，名称本身不绑定具体协议，可继续复用以避免数据库迁移。

## 3. 目标架构

```text
API / Worker
    |
    v
ObjectStorage
    |------------------------------|
    v                              v
LocalObjectStorage             SftpObjectStorage
LOCAL_STORAGE_DIR               SFTP_HOST:SFTP_PORT:SFTP_ROOT
```

`dream-space.storage.mode` 只允许 `local` 和 `sftp`：

- `local`：使用原子临时文件 + rename 写入本地根目录；API 和 Worker 可在同机开发环境共享该目录。
- `sftp`：使用 JSch SFTP 客户端访问远程根目录；API 和 Worker 使用相同的逻辑 key 与 SFTP 根目录。

数据库继续保存 `objectKey` 逻辑 key，例如 `results/{taskId}/{resultId}.webp`。SFTP 适配器将其解析为 `SFTP_ROOT/results/...`，禁止绝对路径、`..`、反斜杠和根目录逃逸。

## 4. SFTP 适配器设计

### 4.1 依赖与连接

- 在 `dream_service/common` 引入 JSch SFTP 客户端依赖，移除 AWS SDK S3 和 URL connection client 依赖。
- 每次操作从受控的客户端工厂取得 SSH client/session/SFTP client，操作完成后归还或关闭；不得在业务线程中共享未同步的 SFTP channel。
- 支持连接、认证、读写和关闭超时；网络临时错误最多按配置重试，重试不得改变逻辑 key。
- 首选 SSH 私钥认证，可兼容密码认证；私钥内容、密码和主机密钥不得进入日志。
- 默认启用 known_hosts 主机密钥校验；仅在明确的本地测试配置下允许关闭，生产禁止 `accept-all`。

### 4.2 文件操作语义

- `put`：校验 key，创建父目录，先上传到同目录 `.upload-{uuid}.tmp`，成功后 rename 为目标文件；目标存在时原子替换。
- `get`：读取远程文件并返回数据库记录对应的 MIME；SFTP 没有对象元数据时由扩展名/调用方 fallback 为 `image/webp`。
- `delete`：删除不存在文件视为成功；其他错误抛出存储异常并记录指标。
- `createSignedGetUrl`：SFTP 不提供签名 URL，继续抛出 `UnsupportedOperationException`。API 和管理端维持现有二进制代理接口，不向客户端返回 SFTP 地址。
- 部分写入失败时删除临时文件；主图成功、缩略图失败时沿用 `GenerationOutputPipeline` 的补偿删除逻辑。

### 4.3 路径与权限

- `ObjectKeyPolicy` 继续作为逻辑 key 的唯一校验入口。
- SFTP 根目录使用 canonical/normalized path 计算，最终路径必须位于根目录下。
- 上传参考图、生成结果、缩略图分别使用既有 `references/`、`results/`、`thumbnails/` 前缀。
- 不允许通过 HTTP 参数指定任意 SFTP key；结果读取仍先做 user -> task -> result 归属校验。

## 5. 配置契约

`dream-space.storage` 改为：

```yaml
dream-space:
  storage:
    mode: ${OBJECT_STORAGE_MODE:local} # local | sftp
    local-directory: ${LOCAL_STORAGE_DIR:D:/softDesign/dream-space/storage}
    sftp:
      host: ${SFTP_HOST:}
      port: ${SFTP_PORT:22}
      username: ${SFTP_USERNAME:}
      password: ${SFTP_PASSWORD:}
      private-key-file: ${SFTP_PRIVATE_KEY_FILE:}
      private-key-passphrase: ${SFTP_PRIVATE_KEY_PASSPHRASE:}
      known-hosts-file: ${SFTP_KNOWN_HOSTS_FILE:}
      strict-host-key-checking: ${SFTP_STRICT_HOST_KEY_CHECKING:true}
      root-directory: ${SFTP_ROOT_DIRECTORY:/dream-space}
      connect-timeout: ${SFTP_CONNECT_TIMEOUT:PT10S}
      operation-timeout: ${SFTP_OPERATION_TIMEOUT:PT60S}
      max-attempts: ${SFTP_MAX_ATTEMPTS:3}
```

设计要求：

- `mode=local` 只校验 `local-directory` 可创建、可读写。
- `mode=sftp` 校验 host、port、username、root-directory，以及 password 或 private-key-file 至少一项；严格主机密钥校验开启时必须提供 known_hosts 文件。
- 删除 `S3_ENDPOINT`、`S3_BUCKET`、`S3_REGION`、`S3_ACCESS_KEY`、`S3_SECRET_KEY`、`S3_SIGNED_URL_TTL_SECONDS` 的业务读取和启动校验。
- API 与 Worker 使用相同的 SFTP 配置命名和根目录；环境变量由部署系统注入。

## 6. Worker local 模式

### 6.1 启动行为

`GenerationWorkerConfiguration` 不再调用 `requireStorageConfiguration()` 强制 S3。改为：

- 真实模型配置仍必须完整：规划 API key/base URL/model、图片 API key/base URL/model/endpoint。
- `storage.mode=local` 时校验本地目录可写，然后正常创建所有真实模型 Bean。
- `storage.mode=sftp` 时校验 SFTP 配置和连通性，然后创建真实模型 Bean。
- 任何存储模式都不允许因为 `local` 而切换到 mock 模型；模型调用、循环优化、评估和审核逻辑保持不变。

### 6.2 结果保存与访问

Worker 继续使用 `GenerationOutputPipeline` 写主图和缩略图，`LocalObjectStorage` 负责落盘。数据库中的 `imagePath/objectKey/thumbnailObjectKey` 保持现有逻辑 key。

API 在 `local` 或 `sftp` 模式均通过 `/dream_web/generation/results/{resultId}/{content|thumbnail}` 读取并代理字节，因此前端无需区分存储模式；管理端结果接口同理。

### 6.3 运行一致性

- 单机 local 开发：API 和 Worker 的 `LOCAL_STORAGE_DIR` 必须指向同一目录。
- 多实例部署：禁止把 local 当作共享存储；必须使用 sftp，或保证所有实例挂载同一共享文件系统。
- 结果写入失败、缩略图失败、任务重试和取消的现有补偿语义保持不变。

## 7. Readiness、指标与错误处理

- `PersistenceReadinessProbe` 抽象为检查当前选定的 `ObjectStorage`：local 检查目录读写；sftp 创建临时文件并删除，验证认证、根目录和权限。
- readiness 不再注入或检查 `S3Client`。
- 增加 `storage.operation`, `storage.mode`, `storage.error`, `storage.retry` 指标；不记录 host 凭据、远程绝对路径或完整 prompt。
- 错误分类至少包括 `STORAGE_CONNECT_FAILED`、`STORAGE_AUTH_FAILED`、`STORAGE_PERMISSION_DENIED`、`STORAGE_NOT_FOUND`、`STORAGE_TIMEOUT`、`STORAGE_WRITE_FAILED`。
- SFTP 连接失败时 Worker readiness 为 false；已入队任务由现有重试/dead-letter 流程处理，不在启动阶段吞掉存储错误。

## 8. 实施顺序

1. 更新 `DreamSpaceProperties`，增加 SFTP 配置对象，将模式枚举从 `local/s3` 改为 `local/sftp`。
2. 实现 `SftpObjectStorage`、连接工厂和路径/临时文件策略；补单元测试。
3. 修改 `SharedPersistenceConfiguration`，移除 S3 beans 和 AWS SDK 依赖，按 mode 注册 local/SFTP adapter。
4. 修改 readiness probe、API/Worker 两套 yml 和注释。
5. 修改 Worker 启动校验，允许 local，保留真实 AI 模型必填校验。
6. 更新配置/架构文档、迁移基线和运行说明；不修改数据库表结构。
7. 执行单元测试、API/Worker 编译测试、SFTP 契约测试、local 端到端测试和结果下载回归。

## 9. 验收标准

- `rg` 检查 API/Worker 业务代码、配置和 Maven 依赖不再出现 S3 client/presigner、S3 配置读取或 `requires ... s3` 启动逻辑。
- `OBJECT_STORAGE_MODE=local` 时 Worker 能启动，readiness 在数据库/Redis/本地目录正常时为 ready，真实模型 Bean 成功创建。
- local Worker 成功处理真实模型返回的图片，主图和缩略图存在于 `LOCAL_STORAGE_DIR`，API 和管理端下载内容正确。
- `OBJECT_STORAGE_MODE=sftp` 时 API 与 Worker 可以上传参考图、保存结果、读取结果、删除临时/失败对象；路径逃逸和未授权访问被拒绝。
- SFTP 主机不可用、认证失败、读写超时、缩略图部分失败均有明确日志/指标和可验证补偿行为。
- 前端、API、Worker 现有任务状态、SSE、额度、审核和重试测试全部通过。

## 10. 回滚与兼容

- 本次不迁移数据库字段；`objectKey` 继续表示逻辑存储 key。
- 发布前需将历史 S3/MinIO 对象迁移到 SFTP 根目录，迁移完成后再切换 `OBJECT_STORAGE_MODE=sftp`。
- 若 SFTP 发布异常，可回滚到上一版本和旧配置，但新版本代码不再支持 S3；回滚窗口内保留旧构建产物。
- local 模式作为开发/单机 fallback 保留，不作为多实例生产共享存储。

# WebSocket压缩功能测试说明

## 功能概述
已成功为MultiPlayer ESP模组添加了WebSocket压缩支持，可以显著减少网络传输数据量。

## 主要更新内容

### 1. Config类更新
- 添加了 `enableCompression` 配置项（默认为true）
- 可通过配置文件控制是否启用压缩功能

### 2. PlayerESPNetworkManager类增强
- 添加了压缩状态管理（compressionEnabled, handshakeCompleted）
- 实现了握手协议支持
- 添加了GZIP解压缩功能
- 实现了二进制消息处理

### 3. 核心功能实现

#### 握手流程
1. 客户端连接后自动发送握手消息
2. 声明客户端是否支持压缩（基于配置）
3. 等待服务器确认并获取实际压缩状态

#### 数据传输优化
- 接收数据时自动检测压缩标志
- 支持压缩和未压缩数据的混合处理
- 自动解压GZIP压缩的数据

#### 兼容性保证
- 保持与原有未压缩协议的完全兼容
- 连接失败时自动重置所有状态
- 错误解压时自动回退到未压缩模式

## 测试验证步骤

### 1. 基本功能测试
- 启动Minecraft客户端
- 打开MultiPlayer ESP配置界面
- 确认可以看到压缩配置选项
- 连接到启用了压缩的服务端

### 2. 压缩效果验证
- 观察日志输出，应显示：
  - "Sent handshake message, compression: true"
  - "Handshake completed. Compression enabled: true"
  - "Received compressed message, size: X -> Y"（当接收到压缩数据时）

### 3. 兼容性测试
- 连接到未启用压缩的传统服务端
- 验证功能仍然正常工作
- 检查不会出现压缩相关错误

## 配置文件示例
```json
{
  "serverURL": "ws://localhost:8765/playeresp",
  "renderDistance": 64,
  "showLines": true,
  "showBoxes": true,
  "boxColor": -2130771968,
  "lineColor": -65536,
  "enableCompression": true
}
```

## 注意事项
1. 压缩功能需要服务端也支持相应的协议
2. 对于小数据包，压缩可能不会带来明显收益
3. 压缩会增加少量CPU开销，但通常被网络传输节省所抵消
4. 可随时通过配置禁用压缩功能

## 故障排除
如果遇到问题：
1. 检查服务端是否支持压缩协议
2. 查看客户端日志中的详细错误信息
3. 尝试禁用压缩功能进行对比测试
4. 确保网络连接稳定
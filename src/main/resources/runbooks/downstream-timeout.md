# 下游服务超时

## 现象

当前服务请求大量超时，但是自身 CPU、内存和线程池可能正常。

日志中可能出现：

- Read timed out
- Connect timed out
- SocketTimeoutException
- downstream request timeout
- HTTP request timeout

Trace 中通常可以看到某个下游服务 Span 耗时异常。

## 排查

1. 查看当前服务整体请求延迟。
2. 搜索 HTTP 或 RPC 超时日志。
3. 查看 Trace，定位耗时最长的下游调用。
4. 检查下游服务自身状态和延迟。
5. 检查 connectTimeout 和 readTimeout 配置。
6. 检查是否存在大量重试导致故障放大。

## 判断

如果当前服务自身指标正常，但 Trace 显示某个下游调用耗时很长，同时存在请求超时日志，则优先判断为下游服务超时。

## 处理

首先处理真正异常的下游服务。

必要时可以：

- 调整合理的超时时间
- 限制重试次数
- 使用熔断或降级
- 检查网络连接

不要盲目增加所有调用的 timeout。
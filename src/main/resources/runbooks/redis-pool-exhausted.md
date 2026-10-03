# Redis 连接池耗尽

## 现象

应用请求延迟明显升高，大量 Redis 操作超时。

日志中可能出现：

- Could not get a resource from the pool
- Timeout waiting for idle object
- RedisConnectionFailureException
- Redis command timeout

应用线程等待 Redis 连接，Redis 服务本身可能仍然正常。

## 排查

1. 查看 Redis 连接池 active、idle、maxActive 指标。
2. 如果 active 长时间接近 maxActive，并且 idle 接近 0，需要重点检查连接池。
3. 搜索连接获取超时相关日志。
4. 检查连接池 maxTotal、maxIdle、minIdle、maxWait 配置。
5. 检查是否存在 Redis 慢命令或连接未及时释放。

## 判断

如果：

- active 接近 maxActive
- idle 为 0
- 出现获取连接超时日志

则很可能是 Redis 连接池耗尽。

## 处理

优先确认连接是否正常释放以及 Redis 请求是否存在阻塞。

必要时可以：

- 调整连接池大小
- 调整 maxWait
- 优化 Redis 慢命令
- 降低瞬时并发

不要仅通过无限增加连接池解决问题。
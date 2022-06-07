## Nacos配置中心 配置变更后同步刷新对象的值（@Value和@NacosValue同时支持刷新）

### 前置条件
必须开启nacos该配置项()
```yaml
nacos:
  config:
    auto-refresh: true
```
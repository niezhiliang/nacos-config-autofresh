## Nacos配置中心 配置变更后同步刷新对象的值（@Value和@NacosValue（无需填写autoRefreshed = true）同时支持刷新）

### 前置条件
- 引入maven依赖
```pom
<dependency>
    <groupId>com.niezhiliang</groupId>
    <artifactId>nacos-config-refresh-spring-boot-starter</artifactId>
    <version>${latest.version}</version>
</dependency>
```
- 开启配置项
```yaml
nacos:
  config:
    auto-refresh: true
```


### 项目开发中，配置刷新功能已实现，后续代码优化完善后会推到maven中央仓库

## Nacos配置中心 配置变更后同步刷新对象的值（@Value和@NacosValue同时支持刷新）

### 前置条件
- 引入maven依赖
```pom
        <dependency>
            <groupId>com.niezhiliang</groupId>
            <artifactId>nacos-refresh--spring-boot-starter</artifactId>
            <version>${latest.version}</version>
        </dependency>
```
- 开启配置项
```yaml
nacos:
  config:
    auto-refresh: true
```


### 项目开发中，后续功能完善以后会推到maven中央仓库

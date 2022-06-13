## Nacos配置中心 配置变更后同步刷新对象的值（@Value和@NacosValue（无需填写autoRefreshed = true）同时支持刷新）

### 使用前置条件

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

### 功能列表

| 功能名称 | 示例 | 是否实现 |
| :-----| ----: | :----: |
| @Value 动态刷新 | @Value("${user.name}") | √ |
| @NacosValue 动态刷新 | @NacosValue("${user.name}") | √ |
| 占位符默认值 | @Value("${user.name:zhangsan}") | √ |
| 占位符嵌套 | @Value("${${user.name}}") | √ (慎用)|

```yaml
# before
xx:
  zz: xx.oo
  oo: hello
# after
xx:
  zz: xx.yy
  yy: helloWorld
```

> 占位符嵌套这种刷新方式不推荐使用，平常工作中单纯的占位符用的也特别少。我也没有找到太好的方式来解决。
> 目前这种情况要支持自动刷新的话，eg: ${${xx.zz}} 我们知道这样的占位符是先从里面一层一层解析的，
> 解析后，先从环境对象中得到`xx.zz`的值`xx.oo`等同于${xx.oo} ,然后去环境对象读取值xx.oo的值 得到hello。
> 如果直接从before方式改为after方式的话，这种是能刷新的。




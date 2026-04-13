# Outline 模块架构

## 模块定位

`outline` 是语言前端层，负责：

1. 使用 `msll` 进行词法/语法解析
2. 将 ParseTree 转换为 `gcp` 可处理的 AST

不负责：

- 类型推导
- 解释执行
- 运行时插件装载

这些能力由 `gcp` 提供。

## 核心对象

## `OutlineParser`

- 对外解析入口
- 通过静态单例 `MyParserBuilder` 缓存 grammar 表，减少重复编译开销
- 支持两种模式：
  - Isolated：`parse(String)` 每次创建新 `ASF`
  - Shared-ASF：构造注入 `GCPConverter`，多次 `parse` 复用同一个 `ASF`

## `GCPConverter`

- 持有 `Map<String, Converter>` 分派表
- 按语法节点名称调用对应 Converter
- 在目标 `ASF` 上创建新 `AST` 并填充节点

## `Converter` 族

- 每个语法簇由独立 converter 处理（如函数、数组、匹配、类型声明）
- 新语法扩展通过“语法规则 + converter + 注册”三步完成

## 资源文件

- `outline/src/main/resources/outlineLexer.gm`
- `outline/src/main/resources/outlineParser.gm`

二者共同定义词法 token 与语法规则，是 converter 分派的来源。

## 关键设计决策

- **语法表静态缓存**：解析性能优先，JVM 生命周期内只初始化一次 grammar。
- **解析状态隔离**：默认单模块调用不会互相污染（isolated mode）。
- **多模块显式开启**：必须使用 shared-ASF 模式，避免调用方误用共享状态。

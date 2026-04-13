# Outline SDK 集成说明

本文档描述 `outline` 模块对外 API 以及和 GCP 的集成边界。

## 模块职责

- `outline`：语法解析 + ParseTree 到 GCP AST 的转换。
- `gcp`：类型推导、解释执行、元数据提取。

`outline` 本身不做推导执行，只负责把源码转成可供 GCP 处理的 AST 结构。

## 关键 API

## `OutlineParser`

路径：`org.twelve.outline.OutlineParser`

### 构造方式

- `new OutlineParser()`：隔离模式（每次 `parse(String)` 新建 `ASF`）
- `new OutlineParser(GCPConverter converter)`：共享 `ASF` 模式（多次 `parse` 累计在同一 `ASF`）

### 解析方法

- `AST parse(String code)`：按当前模式解析源码
- `AST parse(ASF asf, String code)`：显式指定目标 `ASF`（忽略构造器共享 converter）

### 设计要点

- 语法构建器 `MyParserBuilder` 是静态单例，语法表只在 JVM 内初始化一次。
- 语法文件来自类路径：
  - `outlineParser.gm`
  - `outlineLexer.gm`

## `GCPConverter`

路径：`org.twelve.outline.GCPConverter`

职责：

- 管理 `Map<String, Converter>`，按语法节点名分派到具体 `Converter`
- 把 ParserTree 根节点转换为 `AST`（挂入目标 `ASF`）

对扩展方而言，新增语法通常需要：

1. 在 grammar 文件新增规则或 token
2. 新增对应 `Converter` 实现
3. 在 `GCPConverter` 注册映射

## 依赖关系

`outline/pom.xml` 显式依赖：

- `org.twelve:gcp`
- `org.twelve:msll`

因此调用方通常只需引入 `outline`，再由其传递依赖拉起完整解析链路。

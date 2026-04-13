# Grammar 与 Converter 映射

本文档说明 `outline` 的语法层与 AST 转换层如何对应。

## 输入与输出

- 输入：`outlineLexer.gm` + `outlineParser.gm` 解析出的 ParseTree
- 输出：`gcp.ast.AST`（附着于目标 `ASF`）

## 分派机制

`GCPConverter` 内维护 `Map<String, Converter>`：

- key：语法节点名（例如 `function_call`、`outline_declarator`）
- value：对应 converter 实例

解析后从根节点开始递归分派转换。

## 主要映射分组

## 模块与声明

- `root` -> `ProgramConverter`
- `module_statement` -> `ModuleConverter`
- `import_statement` -> `ImportConverter`
- `export_statement` -> `ExportConverter`
- `outline_declarator` -> `OutlineDefinitionConverter`

## 变量与表达式

- `variable_declarator` -> `VarDeclareConverter`
- `assignment` -> `AssignmentConverter`
- `expression` -> `ExpressionConverter`
- `expression_statement` -> `ExpresionStatementConverter`

## 类型系统节点

- `:declared_outline` / `:adt_type` -> `DeclaredTypeConverter`
- `:func_type` -> `FuncTypeConverter`
- `:factor_type` -> `FactorTypeConverter`
- `:array_type` -> `ArrayTypeConverter`
- `:map_type` -> `MapTypeConverter`
- `:nullable_suffix` -> `NullableTypeConverter`

## 调用与访问

- `entity_member_accessor` -> `MemberAccessorConverter`
- `function_call` -> `FunctionCallConverter`
- `reference_call` -> `ReferenceCallConverter`
- `array_map_accessor` -> `AccessorConverter`

## 结构化数据

- `entity` -> `EntityConverter`
- `tuple` -> `TupleConverter`
- `array` -> `ArrayNodeConverter`
- `map` -> `MapNodeConverter`
- `property_assignment` -> `PropertyAssignmentConverter`

## 控制流与高级表达式

- `if_expression` -> `IfConverter`
- `match_expression` -> `MatchExprConverter`
- `ternary_expression` -> `TernaryExprConverter`
- `with_expression` -> `WithConverter`
- `async_expression` -> `AsyncConverter`
- `await_expression` -> `AwaitConverter`

## 扩展语法建议

新增语法时，建议按以下顺序：

1. 在 lexer/parser grammar 中新增规则
2. 实现 converter（必要时新增 wrapper node）
3. 在 `GCPConverter` 注册映射
4. 增加测试覆盖（至少 parse + infer 两层）

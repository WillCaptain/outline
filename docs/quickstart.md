# Outline 5 分钟快速开始

本指南演示如何在 Java 中把 Outline 代码解析为 GCP AST。

## 1. 添加依赖

`outline` 模块本身依赖 `gcp` 与 `msll`。在调用方项目中引入：

```xml
<dependency>
  <groupId>org.example</groupId>
  <artifactId>outline</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

## 2. 解析单模块代码

```java
import org.twelve.outline.OutlineParser;
import org.twelve.gcp.ast.AST;

OutlineParser parser = new OutlineParser(); // isolated mode
AST ast = parser.parse("""
    let x = 42;
    let y = x + 1;
    y
    """);
```

`new OutlineParser()` 使用隔离模式：每次 `parse()` 都创建一个新的 `ASF`，适合单模块场景与测试。

## 3. 多模块解析（共享 ASF）

```java
import org.twelve.outline.OutlineParser;
import org.twelve.outline.GCPConverter;
import org.twelve.gcp.ast.ASF;

ASF asf = new ASF();
OutlineParser parser = new OutlineParser(new GCPConverter(asf)); // shared-ASF mode

parser.parse("module math; let PI = 3.14;");
parser.parse("import PI from math; PI * 2");
```

共享模式下，多次 `parse()` 会累计到同一个 `ASF`，用于跨模块导入解析。

## 4. 后续步骤

- 语言语法参考：`outline-language.md`
- SDK 与模式细节：`sdk-integration.md`

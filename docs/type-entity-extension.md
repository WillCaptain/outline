# Type / Entity 扩展规则

Outline 中的 entity 扩展用于在已有结构基础上得到一个新的结构类型或 future `This`。它不是原地更新，但只要新结构声明了与基类同名的成员，就必须显式说明这个同名成员如何合并。

## 默认规则

默认情况下，同名成员必须满足：

```text
new is old
```

也就是新成员类型必须是旧成员类型的子类型。结果成员类型取 `new`，表示扩展后的结构可以更具体，但不能悄悄放宽旧契约。

```outline
outline Person = {
    age: Number
};

outline Student = Person{
    age: Int      // OK: Int is Number，结果 age: Int
};

outline Bad = Person{
    age: String   // ERROR: String 不是 Number
};
```

同样的规则也适用于 future `This`：

```outline
outline Box = {
    value: Number,
    narrow: Unit -> this{
        value = 1   // OK: Int is Number
    }
};
```

## override

`override` 表示显式覆盖字段契约，结果成员类型取 `new`。

它允许两种兼容方向：

```text
new is old
old is new
```

因此 `override` 可以用于放宽一个已有字段：

```outline
outline Narrow = {
    value: Int
};

outline Wide = Narrow{
    override value: Number   // OK: Int is Number，结果 value: Number
};
```

如果两个类型互不兼容，`override` 仍然报错。它不是 union，也不是 overload。

## overload

`overload` 表示显式合并字段类型。合并结果会规约到最小表达：

```text
new is old  -> 结果 old
old is new  -> 结果 new
否则        -> 结果 Poly(old, new)
```

```outline
outline SymbolBase = {
    value: Source
};

outline SameAsBase = SymbolBase{
    overload value: Source       // 结果仍是 Source
};

outline Wider = SymbolBase{
    overload value: ?            // 结果 ?
};

outline Either = SymbolBase{
    overload value: Map          // 结果 Source & Map 的 Poly 形态
};
```

## 设计原则

默认重名是收窄，不是合并。`override` 是覆盖类型，`overload` 才是显式合并类型。这样可以避免扩展时因为同名字段自动产生 `Poly`，把真正的字段契约错误藏起来。

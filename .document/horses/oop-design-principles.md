# 面向对象基本设计原则

## SOLID 原则

### 1. 单一职责原则 (Single Responsibility Principle, SRP)
> 一个类只应有一个引起它变化的原因；即一个类只负责一项职责。

- 每个类/模块应当只有一个明确的职责
- 职责模糊导致代码耦合、难以维护
- 是模块划分和类设计最基础的原则

### 2. 开闭原则 (Open-Closed Principle, OCP)
> 软件实体（类、模块、函数等）应对扩展开放，对修改关闭。

- 通过抽象和接口实现行为扩展
- 新增功能时优先考虑新增代码而非修改已有代码
- 与策略模式、模板方法模式等配合使用

### 3. 里氏替换原则 (Liskov Substitution Principle, LSP)
> 子类必须能替换其父类，且不改变程序的正确性。

- 子类不能削弱父类的前置条件
- 子类不能增强父类的后置条件
- 违反 LSP 的继承关系应考虑用组合替代

### 4. 接口隔离原则 (Interface Segregation Principle, ISP)
> 不应强迫客户端依赖它们不需要的接口。

- 胖接口应拆分为多个专用接口
- 客户端只依赖它实际调用的方法
- 避免接口污染和无效依赖

### 5. 依赖倒置原则 (Dependency Inversion Principle, DIP)
> 高层模块不应依赖低层模块，二者都应依赖抽象；抽象不应依赖细节，细节应依赖抽象。

- 接口/抽象类属于高层，实现属于细节
- 依赖关系应指向抽象而非具体实现
- Spring 中的依赖注入（DI）是该原则的典型实践

---

## 包/模块级设计原则

### 6. 稳定依赖原则 (Stable Dependencies Principle, SDP)
> 包的依赖方向应指向比自身更稳定的方向。

- 被依赖越多 → 越稳定（难以修改）
- 变化频繁的模块不应被稳定的模块依赖
- **常见的违反**：下层模块（如 boot）实现上层模块（如 console）声明的接口，导致上层间接依赖下层

### 7. 稳定抽象原则 (Stable Abstractions Principle, SAP)
> 包的稳定程度与其抽象程度成正比：越稳定的包应越抽象，越不稳定的包应越具体。

- 稳定包（被大量依赖）应包含大量接口/抽象类
- 不稳定包（变化频繁）应包含具体实现
- 抽象程度与稳定程度的失衡会导致设计僵化或脆弱

### 8. 共同封闭原则 (Common Closure Principle, CCP)
> 因相同原因、在同一时间发生变化的事物应放在同一个包中。

- 包的内聚性依据：变更原因
- 与单一职责原则对应的包级别版本
- 有助于最小化发布范围

### 9. 共同复用原则 (Common Reuse Principle, CRP)
> 一个包中的所有类应被一起复用；不应强迫包的消费者依赖它们不需要的类。

- 与接口隔离原则对应的包级别版本
- 与 CCP 互为张力 —— 需要在二者间权衡

---

## 其他基本原则

### 10. DRY (Don't Repeat Yourself)
> 每份知识在系统中应只有单一、明确、权威的表述。

- 消除重复代码和知识
- 重复意味着维护时容易遗漏

### 11. KISS (Keep It Simple, Stupid)
> 简单的设计方案优于复杂的设计方案。

- 除非有明确的必要，不要引入额外的复杂性
- 最简单的方案通常最容易理解和维护

### 12. YAGNI (You Ain't Gonna Need It)
> 只在真正需要时才添加功能，不要为未来可能的需求预先添加。

- 避免过度设计
- 降低当前开发成本和未来维护负担

### 13. 组合优于继承 (Composition over Inheritance)
> 优先使用对象组合而非类继承来复用行为。

- 继承破坏了封装性，组合更灵活
- 组合可以在运行时改变行为
- 避免继承层次过深

### 14. 迪米特法则 (Law of Demeter, LoD)
> 一个对象应尽可能少地与其他对象发生交互；只与直接朋友通信。

- "只跟朋友说话，不跟陌生人说话"
- 减少类之间的耦合度
- 链式调用 `a.b().c().d()` 通常是违反 LoD 的信号

### 15. 好莱坞原则 (Hollywood Principle)
> "不要打电话给我们，我们会打给你" —— 控制权反转。

- 框架调用应用程序代码，而非应用程序调用框架
- IoC/Dependency Injection 的核心思想

---

## 稳定依赖原则（SDP）在项目中的体现

以本项目（CloudDM）的模块依赖为例：

```
cgdm-console  (声明 SystemStatusProvider 接口)
     ↓ 违反 SDP — 接口倒置后修复为:
boot-initialization  (声明接口 + 实现)
     ↑
boot-alone / boot-console  (使用接口)
```

**修正前的问题**：
- `cgdm-console` 定义了 `SystemStatusProvider` 接口
- `boot-initialization` 实现了该接口
- `cgdm-console` 是上层、变化少的模块，但它的接口被下层实现 → `cgdm-console` 间接依赖 `boot-initialization`（违反稳定依赖）

**修正方案**：
- 将接口定义下移到最稳定的依赖方向
- `SystemStatusProvider` → 从 `cgdm-console` 移至 `boot-initialization`
- 依赖方向变为 `boot → boot-init`，不再反向

---

## 参考资料

- Robert C. Martin, *Agile Software Development, Principles, Patterns, and Practices*
- Robert C. Martin, *Clean Architecture*
- 包设计原则（SDP/SAP/CCP/CRP）首次在 *C++ Report* 中提出，后被收录在 Martin 的著作中。

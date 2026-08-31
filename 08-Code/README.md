# 08-Code — 代码仓库

> 本目录存放各类参考实现代码，与知识库文档（01-07）配套使用。

## 目录结构

```
08-Code/
├── README.md                    # 本文件
└── state-machine/               # 自研轻量级状态机引擎实现
    ├── README.md                # 状态机项目说明
    ├── pom.xml                  # Maven 配置
    └── src/
        ├── main/java/com/selfdevelopment/ai/messaging/statemachine/
        └── test/java/com/selfdevelopment/ai/messaging/statemachine/
```

## 项目列表

| 项目 | 路径 | 说明 | 对应知识库 |
|------|------|------|-----------|
| state-machine | [./state-machine](./state-machine) | 自研轻量级状态机引擎（无状态、表驱动、零依赖） | [01-CBOL-Domain-Knowledge/state-machine/](../01-CBOL-Domain-Knowledge/state-machine/) |

## 使用说明

每个子项目都是独立的 Maven 项目，可以单独构建和测试：

```bash
cd state-machine
mvn clean test
mvn package
```

## 设计原则

- **零外部依赖**：核心引擎仅依赖 JDK 标准库
- **与知识库同步**：代码实现严格遵循对应知识库文档的设计
- **可独立运行**：每个项目都有完整的测试用例和构建配置
- **包名规范**：`com.selfdevelopment.ai.messaging.{module}`

---

*08-Code — last updated 2026-08-31*

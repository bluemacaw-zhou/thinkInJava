# Maven 依赖管理最佳实践：Spring Boot Parent 继承问题解决方案

## 背景

在多模块 Maven 项目中，我们希望通过自定义的 parent POM 来统一管理 Spring Boot 依赖版本。具体需求是：

- `spring.dependencies` 继承 `spring-boot-starter-parent` (3.4.1)
- 子模块（如 `mongodb`）继承 `spring.dependencies`
- 子模块的依赖无需指定版本号，自动继承 Spring Boot 3.4.1 的版本管理

## 项目结构

```
ThinkInJava (根 POM)
├── spring.dependencies (继承 spring-boot-starter-parent)
└── mongodb (继承 spring.dependencies)
```

## 遇到的问题

### 错误信息

```
[ERROR] 'dependencies.dependency.version' for org.springframework.boot:spring-boot-starter:jar is missing.
[ERROR] 'dependencies.dependency.version' for org.springframework.boot:spring-boot-starter-web:jar is missing.
[ERROR] 'dependencies.dependency.version' for org.springframework.boot:spring-boot-starter-data-mongodb:jar is missing.
```

### 问题分析

最初在 `spring.dependencies` 的 `dependencyManagement` 中声明了 Spring Boot 依赖但**没有指定版本号**：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
            <!-- 没有 version -->
        </dependency>
        <!-- ... 其他依赖 ... -->
    </dependencies>
</dependencyManagement>
```

**核心问题**：在 `dependencyManagement` 中重新声明这些依赖（即使没有版本号），会**覆盖**从 `spring-boot-starter-parent` 继承来的版本管理信息。Maven 在解析子模块的依赖时，发现依赖没有版本号就会报错。

## 解决方案

### 关键原则

> **只在 `dependencyManagement` 中声明需要自定义版本的依赖，不要重复声明已经在父 POM 中管理的依赖**

### 正确的配置

#### 1. spring.dependencies/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承 Spring Boot Parent -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <groupId>io.bluemacaw</groupId>
    <artifactId>spring.dependencies</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- ❌ 不要声明 Spring Boot 的依赖 -->
            <!-- 它们已经从 spring-boot-starter-parent 继承 -->

            <!-- ✅ 只声明需要自定义版本的第三方依赖 -->

            <!--连接H2的mybatis-->
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>3.5.5</version>
            </dependency>

            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>druid-spring-boot-starter</artifactId>
                <version>1.2.16</version>
            </dependency>

            <dependency>
                <groupId>com.mysql</groupId>
                <artifactId>mysql-connector-j</artifactId>
                <version>8.3.0</version>
            </dependency>

            <dependency>
                <groupId>com.alibaba</groupId>
                <artifactId>fastjson</artifactId>
                <version>1.2.83</version>
            </dependency>

            <!--elasticsearch-->
            <dependency>
                <groupId>jakarta.json</groupId>
                <artifactId>jakarta.json-api</artifactId>
                <version>2.1.3</version>
            </dependency>

            <dependency>
                <groupId>org.elasticsearch.client</groupId>
                <artifactId>elasticsearch-rest-client</artifactId>
                <version>8.15.5</version>
            </dependency>

            <dependency>
                <groupId>org.elasticsearch</groupId>
                <artifactId>elasticsearch</artifactId>
                <version>8.15.5</version>
            </dependency>

            <dependency>
                <groupId>co.elastic.clients</groupId>
                <artifactId>elasticsearch-java</artifactId>
                <version>8.15.5</version>
            </dependency>

            <dependency>
                <groupId>javax.xml.bind</groupId>
                <artifactId>jaxb-api</artifactId>
                <version>2.3.1</version>
            </dependency>

            <dependency>
                <groupId>org.glassfish.jaxb</groupId>
                <artifactId>jaxb-runtime</artifactId>
                <version>2.3.1</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

#### 2. mongodb/pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <!-- 继承自定义的 spring.dependencies -->
    <parent>
        <groupId>io.bluemacaw</groupId>
        <artifactId>spring.dependencies</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../spring.dependencies/pom.xml</relativePath>
    </parent>

    <artifactId>mongodb</artifactId>

   <dependencies>
       <!-- ✅ Spring Boot 依赖无需指定版本 -->
       <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter</artifactId>
       </dependency>

       <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-web</artifactId>
       </dependency>

       <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-data-mongodb</artifactId>
       </dependency>

       <dependency>
           <groupId>org.projectlombok</groupId>
           <artifactId>lombok</artifactId>
           <scope>provided</scope>
       </dependency>
   </dependencies>

</project>
```

## 部署步骤

### 1. 安装 spring.dependencies 到本地仓库

```bash
mvn clean install -f "spring.dependencies/pom.xml"
```

### 2. 编译子模块

```bash
mvn clean compile -f "mongodb/pom.xml"
```

## 依赖继承链路

```
spring-boot-starter-parent (3.4.1)
    ↓ (parent - 版本管理自动继承)
spring.dependencies (1.0.0-SNAPSHOT)
    ├─ 继承：所有 Spring Boot 依赖的版本
    └─ 自定义：第三方依赖版本（mybatis-plus, elasticsearch 等）
        ↓ (parent)
mongodb 模块
    └─ 继承：Spring Boot + 自定义依赖的所有版本
```

## Maven 依赖管理原理

### 1. Parent POM 继承

当一个 POM 继承另一个 POM 时（通过 `<parent>`），它会继承：
- `<dependencyManagement>` 中定义的依赖版本
- `<properties>` 属性
- `<build>` 配置
- 等等

### 2. dependencyManagement 的作用

- **不会实际引入依赖**，只是声明版本管理
- 子模块在使用这些依赖时，可以省略版本号
- **会覆盖父 POM 中相同 groupId:artifactId 的依赖管理**

### 3. 关键理解

❌ **错误做法**：
```xml
<parent>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.1</version>
</parent>

<dependencyManagement>
    <dependencies>
        <!-- 这会覆盖父 POM 的版本管理 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
            <!-- 没有 version，导致子模块报错 -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

✅ **正确做法**：
```xml
<parent>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.1</version>
</parent>

<dependencyManagement>
    <dependencies>
        <!-- 不要重复声明父 POM 已管理的依赖 -->
        <!-- 只声明需要自定义版本的依赖 -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.5</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 验证方法

### 查看有效 POM

```bash
mvn help:effective-pom -f "mongodb/pom.xml"
```

这个命令会显示合并后的 POM，可以看到所有继承的依赖管理。

### 查看依赖树

```bash
mvn dependency:tree -f "mongodb/pom.xml"
```

可以看到每个依赖的实际版本。

## 常见陷阱

### 1. POM 元素顺序

Maven POM 的元素顺序很重要，必须按照以下顺序：

```xml
<modelVersion>
<parent>
<groupId>
<artifactId>
<version>
<packaging>
<properties>
<dependencyManagement>
<dependencies>
<build>
```

### 2. relativePath 的作用

```xml
<parent>
    <groupId>io.bluemacaw</groupId>
    <artifactId>spring.dependencies</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../spring.dependencies/pom.xml</relativePath>
</parent>
```

- `relativePath` 告诉 Maven 在哪里找父 POM
- 如果不指定，Maven 会先在本地仓库查找
- 对于 SNAPSHOT 版本，建议明确指定，避免解析问题

### 3. 需要先安装父 POM

如果子模块单独编译（不是从根 POM 编译），必须先将父 POM 安装到本地仓库：

```bash
mvn install -f "spring.dependencies/pom.xml"
```

## 最佳实践总结

1. ✅ **让父 POM 继承 spring-boot-starter-parent**
2. ✅ **不要在 dependencyManagement 中重复声明已继承的依赖**
3. ✅ **只管理需要自定义版本的依赖**
4. ✅ **子模块的依赖不要写版本号**
5. ✅ **使用 relativePath 明确指定父 POM 位置**
6. ✅ **父 POM 使用 `<packaging>pom</packaging>`**

## 参考资料

- [Maven Dependency Management](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
- [Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/)
- [Maven POM Reference](https://maven.apache.org/pom.html)

---

**日期**: 2025-10-20
**作者**: Claude
**环境**: Maven 3.8.1, Spring Boot 3.4.1, Java 17

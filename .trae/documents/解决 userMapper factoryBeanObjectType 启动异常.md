## 根因简述
- MyBatis 与 Spring 6/Boot 3 兼容性问题：`mybatis-spring` 2.x 在 Spring 6 环境下的 `MapperFactoryBean#getObjectType` 元数据解析不兼容，触发 `factoryBeanObjectType` 类型为 `java.lang.String` 的异常。
- Mapper 注册重复或错配：同一包被多处 `@MapperScan` 扫描、同时在接口上使用 `@Mapper` 注解、并与多数据源的不同 `SqlSessionFactory` 叠加，容易造成 BeanDefinition 元数据冲突。

## 修复步骤
1. 统一依赖版本（必须）
- 在 `pom.xml` 明确增加并对齐 `org.mybatis:mybatis-spring:3.0.3`（或 3.x 最新稳定版），确保与 Spring 6/Boot 3 兼容。

2. 统一 Mapper 扫描来源（必须）
- 在应用主类 `LinAgentApplication` 上使用唯一的 `@MapperScan("com.lin.linagent.mapper")`。
- 删除数据源配置类上的 `@MapperScan(...)`，避免同一包被多工厂重复扫描。
- 去除 `UserMapper` 接口上的 `@Mapper` 注解，仅依赖包级扫描生成代理。

3. 简化 SqlSessionFactory（可选但建议）
- 若当前仅使用 MySQL 作为主库：保留一个 `SqlSessionFactory` 即可，避免多工厂加载同一命名空间的 Mapper/XML。
- 若确有多数据源：将不同数据源的 Mapper 分包，例如：
  - `com.lin.linagent.mysql.mapper` 绑定 `mysqlSqlSessionFactory`
  - `com.lin.linagent.pg.mapper` 绑定 `pgSqlSessionFactory`
  并分别在对应数据源配置类上 `@MapperScan(basePackages=..., sqlSessionFactoryRef=...)`，同时移除应用主类的通用扫描。

4. XML 位置与配置（核对）
- 保持 XML 放在 `classpath:/mapper/**/*.xml`（当前 `UserMapper.xml` 路径符合惯例），无需额外配置；如自定义路径，请在 `application.yaml` 的 `mybatis-plus.mapper-locations` 中显式指定。

## 验证步骤
- 清理并重启：删除 `target/`，运行 `./mvnw.cmd -DskipTests spring-boot:run`（确保 `JAVA_HOME` 指向 JDK 21）。
- 关注启动日志：不应再出现 `Invalid value type for attribute 'factoryBeanObjectType'`；Tomcat 应在端口 `8123` 启动。
- 功能冒烟：访问基础健康或任意控制器接口，确认无 MyBatis 代理错误。

## 风险与回滚
- 依赖版本上调为 `mybatis-spring 3.x` 是兼容性修复，风险低；如遇到旧写法不兼容，按 3.x 迁移指南调整。
- 若业务确需多数据源混用同包 Mapper，则必须改为“分包+分别扫描”，否则会再次出现 Bean 元数据冲突。

## 交付内容
- 更新 `pom.xml` 增加 `mybatis-spring:3.0.3`。
- 更新 `LinAgentApplication`：添加唯一扫描 `@MapperScan("com.lin.linagent.mapper")`。
- 移除两处数据源配置的 `@MapperScan` 与 `UserMapper` 的 `@Mapper` 注解。
- 完成本地启动验证，无上述异常。请确认是否按此方案执行。
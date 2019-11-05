# mybatis-mapper-spring
[![mybatis-mapper-spring](https://img.shields.io/badge/plugin-mybatis--mapper--spring-green)](https://github.com/tangxbai/mybatis-mappe-spring) ![version](https://img.shields.io/badge/release-1.3.0-blue) [![maven central](https://img.shields.io/badge/maven%20central-1.3.0-brightgreen)](https://maven-badges.herokuapp.com/maven-central/org.mybatis/mybatis) [![license](https://img.shields.io/badge/license-Apache%202.0-blue)](http://www.apache.org/licenses/LICENSE-2.0.html)

mybatis-mapper和spring的中间件，用于整合spring和mybatis-mapper组件。更好的将mybatis-mapper应用到spring各组件中。

这是一个很基础的组件，包括以后在springboot也会被大量使用到。



## 关联文档

关于纯java环境，请移步到：https://github.com/tangxbai/mybatis-mapper

关于整合springboot，请移步到：https://github.com/tangxbai/mybatis-mapper-spring-boot



## 项目演示

- java + mybatis-mapper - [点击获取]( https://github.com/tangxbai/mybatis-mapper-demo)
- spring + mybatis-mapper - [点击获取]( https://github.com/tangxbai/mybatis-mapper-spring-demo)
- springboot + mybatis-mapper - [点击获取](https://github.com/tangxbai/mybatis-mapper-spring-boot/tree/master/mybatis-mapper-spring-boot-samples)



## 快速开始

```xml
<dependency>
    <groupId>com.viiyue.plugins</groupId>
    <artifactId>mybatis-mapper-spring</artifactId>
    <version>[VERSION]</version>
</dependency>
```

如何获取最新版本？[点击这里获取最新版本](https://search.maven.org/search?q=g:com.viiyue.plugins%20AND%20a:mybatis-mapper-spring&core=gav)



## 基础配置

关于spring.xml的配置方式，这里罗列出一些会涉及到的Bean配置，其他省略与插件无关部分。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans>
    <!-- DataSource( 选择你中意的数据源就好 ) -->
    <bean id="dataSource" class="xxx.xxx.xxx.DataSource">
        ...
    </bean>

    <!-- SqlSessionFactory( 更换为插件扩展类，除了新增一些偏好配置，其他无异 ) -->
    <bean id="sqlSessionFactory" class="com.viiyue.plugins.mybatis.spring.MyBatisMapperSqlSessionFactoryBean">
        <property name="dataSource" ref="dataSource" />

        <!-- 省略其他配置 -->
        <!-- <property name="..." value="..." /> -->
        
        <!-- 集成偏好配置，可选，有默认值 -->
		<property name="enableLogger" value="true" />
        <property name="enableMapperScanLog" value="true" />
		<property name="enableRuntimeLog" value="true" />
		<property name="enableCompilationLog" value="true" />
		<property name="enableXmlSyntaxParsing" value="true" />
		<property name="enableKeywordsToUppercase" value="true" />
		<property name="databaseColumnStyle" value="#" />
        
        <!-- 实体对象别名配置，必须 -->
        <property name="typeAliasesPackage" value="xxx.xxx.xxx.model" />

        <!-- mybatis.xml，可选 -->
        <!-- <property name="configLocation" value="classpath:mybatis.xml" /> -->

        <!-- 配置XML文件，可选 -->
        <property name="mapperLocations" value="classpath:mapper/*Mapper.xml" />
    </bean>

    <!-- Mapper scanner( 与原始保持一致 ) -->
    <bean id="mapperScannerConfigurer" class="org.mybatis.spring.mapper.MapperScannerConfigurer">
        <property name="basePackage" value="..." />
        <property name="annotationClass" value="org.springframework.stereotype.Repository" />
        <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory" />
    </bean>
</beans>
```

这里因为一些特别的原因无法在原始的SqlSessionFactoryBean中顺利加入一些扩展配置，所以这里扩展了额外的MyBatisMapperSqlSessionFactoryBean，用于替代原始的SqlSessionFactoryBean，除了集成了**偏好配置**等，其他与原始SqlSessionFactoryBean保持一样。



## 配置数据库Bean

```java
@Table( prefix = "t_" ) // 表名生成规则，可以配置更多详细说明
@NamingRule( NameStyle.UNDERLINE ) // 字段和数据库列之间的转换规则
@ValueRule( ValueStyle.SHORT ) // 值的生成规则，类似于：#{id, javaType=Long, jdbcType=BIGINT}
@ExpressionRule( ExpressionStyle.SHORT ) // 表达式生成规则，类似于: id = #{id, javaType=Long, jdbcType=BIGINT}
@DefaultOrderBy( "#pk" ) // #pk主键占位符，指向当前生效的主键字段，也可以直接写 "id"。
public class YourModelBean {

    @Id // 主键可以配置多个，但是只会有一个生效，Api方法中如果想要使用其他主键请指明所在下标位置
    @Index( Integer.MIN_VALUE )
    @GeneratedKey( useGeneratedKeys = true ) // JDBC支持的自增主键获取方式
	//	@GeneratedKey( valueProvider = SnowFlakeIdValueProvider.class ) // 雪花Id，插件提供的两种主键生成策略之一
	//	@GeneratedKey( statement = "MYSQL" ) // 枚举引用
	//	@GeneratedKey( statement = "SELECT LAST_INSERT_ID()" ) // 自增主键SQL查询语句
	//	@GeneratedKey( statementProvider = YourCustomStatementProvider.class ) // 通过Provider提供SQL语句
    private Long id;

    @Index( Integer.MAX_VALUE - 4 )
    @Column( jdcbType = Type.CHAR ) // 对字段进行详细描述
    @LogicallyDelete( selectValue = "Y", deletedValue = "N" ) // 开启逻辑删除支持，只能配置一次
    private Boolean display;

    @Index( Integer.MAX_VALUE - 3 )
    private Date createTime;

    @Index( Integer.MAX_VALUE - 2 )
    private Date modifyTime;

    @Version // 开启乐观锁支持，只能配置一次
    @Index( Integer.MAX_VALUE - 1 )
    @Column( insertable = false )
    private Long version;

    // @Index主要对字段出现顺序进行干扰，对字段进行干扰以后，输出的顺序大概是这样：
    // => id, ..., display, create_time, modify_time, version
    // 如果您未使用@Index注解，那么字段的原始顺序是这样的：
    // => id, display, create_time, modify_time, version, ...
    // 默认输出会将父类的字段排在最前面
    
    // setter/getter...

}
```



## 配置Mapper

```java
@Repository
public interface AccountMapper extends Mapper<Account, AccountDTO, Long> {
    // 你自己的一些Api方法
}
```

关于`@Repository`注解，您需要通过MapperScannerConfigurer的各种属性配置来指定关于Mapper的扫描规则，各位根据自己的使用习惯进行配置，因为插件已经使用了Mapper这个名字，所以不建议继续使用`@Mapper`这个注解。



## 使用方式

在使用上没什么特别需要注意的，原来怎么使用现在还怎么使用，唯一需要注意的只有一些配置而已，所以在使用方面请参照大家以前的使用方式，这里就不作过多的说明了，如有不清楚的地方，请拉取demo项目查看示例。或者你也可以 [点击这里查看更详细的文档](https://github.com/tangxbai/mybatis-mapper#如何使用)。



## 关于作者

- QQ群：947460272
- 邮箱：tangxbai@hotmail.com
- 掘金： https://juejin.im/user/5da5621ce51d4524f007f35f
- 简书： https://www.jianshu.com/u/e62f4302c51f
- Issuse：https://github.com/tangxbai/mybatis-mapper-spring/issues

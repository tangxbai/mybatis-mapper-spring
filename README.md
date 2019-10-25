# mybatis-mapper-spring
[![mybatis-mapper-spring](https://img.shields.io/badge/plugin-mybatis--mapper--spring-green)](https://github.com/tangxbai/mybatis-mappe-spring) ![version](https://img.shields.io/badge/release-1.2.0-blue) [![maven central](https://img.shields.io/badge/maven%20central-1.2.0-brightgreen)](https://maven-badges.herokuapp.com/maven-central/org.mybatis/mybatis) [![license](https://img.shields.io/badge/license-Apache%202.0-blue)](http://www.apache.org/licenses/LICENSE-2.0.html)

Mybatis-mapper Spring中间件，用于整合Spring和Mybatis-mapper组件。



## 关联文档

关于java，请移步到：https://github.com/tangxbai/mybatis-mapper

关于整合springboot，请移步到：https://github.com/tangxbai/mybatis-mapper-spring-boot-starter



## 项目演示

- java + mybatis-mapper - [点击获取]( https://github.com/tangxbai/mybatis-mapper-demo)
- spring + mybatis-mapper- [点击获取]( https://github.com/tangxbai/mybatis-mapper-spring-demo)
- springboot + mybatis-mapper- [点击获取]( https://github.com/tangxbai/mybatis-mapper-spring-boot-starter-demo)



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

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans>
    <!-- DataSource -->
    <bean id="dataSource" class="xxx.xxx.xxx.DataSource">
        ...
    </bean>

    <!-- SqlSessionFactory -->
    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
        <property name="dataSource" ref="dataSource" />

        <!-- 省略其他配置 -->
        <!-- <property name="..." value="..." /> -->

        <!-- mybatis.xml，可选 -->
        <!-- <property name="configLocation" value="classpath:mybatis.xml" /> -->

        <!-- 配置XML文件，可选 -->
        <property name="mapperLocations" value="classpath:mapper/*Mapper.xml" />

        <!-- 实体对象别名配置，必须 -->
        <property name="typeAliasesPackage" value="xxx.xxx.xxx.model" />

        <!-- XML模板语法解析，可选 -->
        <property name="defaultScriptingLanguageDriver" value="com.viiyue.plugins.mybatis.MyBatisMapperLanguageDriver"/>

        <!-- 启用插件，必须 -->
        <property name="sqlSessionFactoryBuilder">
            <bean class="com.viiyue.plugins.mybatis.MyBatisMapperFactoryBuilder" />
        </property>

        <!-- 偏好配置，可选，有默认值 -->
        <property name="configurationProperties">
            <props>
                <prop key="enableLogger">true</prop>
                <prop key="enableRuntimeLog">true</prop>
                <prop key="enableCompilationLog">true</prop>
                <prop key="enableOptimisticLock">true</prop>
                <prop key="enableKeywordsToUppercase">true</prop>
                <prop key="databaseColumnStyle">#</prop>
            </props>
        </property>
    </bean>

    <!-- Mapper scanner -->
    <bean id="mapperScannerConfigurer" class="org.mybatis.spring.mapper.MapperScannerConfigurer">
        <property name="basePackage" value="..." />
        <property name="annotationClass" value="org.springframework.stereotype.Repository" />
        <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory" />
    </bean>
</beans>
```

*这里几乎没有破坏任何的原始使用方式，只是加入了一些Spring Bean的附加属性配置。*



## 使用方式

在使用上没什么特别需要注意的，原来怎么使用现在还怎么使用，唯一需要注意的只有一些配置而已，所以在使用方面请参照大家以前的使用方式，这里就不作过多的说明了，如有不清楚的地方，请拉取demo项目查看示例。



## 关于作者

- QQ群：947460272
- 邮箱：tangxbai@hotmail.com
- 掘金： https://juejin.im/user/5da5621ce51d4524f007f35f
- 简书： https://www.jianshu.com/u/e62f4302c51f
- Issuse：https://github.com/tangxbai/mybatis-mapper-spring/issues

唐小白，一名90后程序猿，主攻JAVA，喜欢瞎研究各种框架源代码，偶尔会冒出一些奇怪的想法，欢迎各位同学前来吐槽。 

/**
 * Copyright (C) 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viiyue.plugins.mybatis.spring;

import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;
import static org.springframework.util.ObjectUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.tokenizeToStringArray;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.NestedIOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.util.ClassUtils;

import com.viiyue.plugins.mybatis.MyBatisMapperLanguageDriver;
import com.viiyue.plugins.mybatis.scripting.MyBatisMapperBuilder;
import com.viiyue.plugins.mybatis.utils.LoggerUtil;

/**
 * <p>
 * Rewrite the {@link SqlSessionFactoryBean} and add the business logic of
 * mybatis-mapper so that the function of mybatis-mapper can penetrate mybatis
 * for seamless integration.
 *
 * <p>
 * The logic code of the original {@link SqlSessionFactoryBean} is not changed at all,
 * but the processing logic of mybatis-mapper is added on this basis.
 *
 * @author Putthiphong Boonphong
 * @author Hunter Presnall
 * @author Eduardo Macarron
 * @author Eddú Meléndez
 * @author Kazuki Shimizu
 * @author tangxbai
 * @since mybatis-spring 2.0.3
 * @since mybatis-mapper 1.3.0
 * @since mybatis-mapper-spring 1.3.0
 * 
 * @see SqlSessionFactoryBean
 */
public final class MyBatisMapperSqlSessionFactoryBean implements FactoryBean<SqlSessionFactory>, InitializingBean, ApplicationListener<ApplicationEvent> {

	private static final Logger LOGGER = LoggerFactory.getLogger( MyBatisMapperSqlSessionFactoryBean.class );

	private static final ResourcePatternResolver RESOURCE_PATTERN_RESOLVER = new PathMatchingResourcePatternResolver();
	private static final MetadataReaderFactory METADATA_READER_FACTORY = new CachingMetadataReaderFactory();

	// mybatis-spring original variables
	
	private Resource configLocation;
	private Configuration configuration;
	private Resource [] mapperLocations;
	private DataSource dataSource;
	private TransactionFactory transactionFactory;
	private Properties configurationProperties;
	private SqlSessionFactoryBuilder sqlSessionFactoryBuilder = new SqlSessionFactoryBuilder();
	private SqlSessionFactory sqlSessionFactory;

	// EnvironmentAware requires spring 3.1
	private String environment = SqlSessionFactoryBean.class.getSimpleName();

	private Interceptor [] plugins;
	private TypeHandler<?> [] typeHandlers;
	private String typeHandlersPackage;
	private Class<?> [] typeAliases;
	private String typeAliasesPackage;
	private Class<?> typeAliasesSuperType;
	private LanguageDriver [] scriptingLanguageDrivers;
	private Class<? extends LanguageDriver> defaultScriptingLanguageDriver;

	// issue #19. No default provider.
	private DatabaseIdProvider databaseIdProvider;
	private Class<? extends VFS> vfs;
	private Cache cache;
	private ObjectFactory objectFactory;
	private ObjectWrapperFactory objectWrapperFactory;

	// mybatis-mapper variables
	
	private boolean enableXmlSyntaxParsing;
	private final MyBatisMapperBuilder mybatisMapperBuilder = new MyBatisMapperBuilder();

	/**
	 * Sets the ObjectFactory.
	 *
	 * @param objectFactory a custom ObjectFactory
	 * @since mybatis-spring 1.1.2
	 */
	public void setObjectFactory( ObjectFactory objectFactory ) {
		this.objectFactory = objectFactory;
	}

	/**
	 * Sets the ObjectWrapperFactory.
	 *
	 * @param objectWrapperFactory a specified ObjectWrapperFactory
	 * @since mybatis-spring 1.1.2
	 */
	public void setObjectWrapperFactory( ObjectWrapperFactory objectWrapperFactory ) {
		this.objectWrapperFactory = objectWrapperFactory;
	}

	/**
	 * Gets the DatabaseIdProvider
	 *
	 * @return a specified DatabaseIdProvider
	 * @since mybatis-spring 1.1.0
	 */
	public DatabaseIdProvider getDatabaseIdProvider() {
		return databaseIdProvider;
	}

	/**
	 * Sets the DatabaseIdProvider. As of version 1.2.2 this variable is not initialized by default.
	 *
	 * @param databaseIdProvider a DatabaseIdProvider
	 * @since mybatis-spring 1.1.0
	 */
	public void setDatabaseIdProvider( DatabaseIdProvider databaseIdProvider ) {
		this.databaseIdProvider = databaseIdProvider;
	}

	/**
	 * Gets the VFS.
	 *
	 * @return a specified VFS
	 */
	public Class<? extends VFS> getVfs() {
		return this.vfs;
	}

	/**
	 * Sets the VFS.
	 *
	 * @param vfs a VFS
	 */
	public void setVfs( Class<? extends VFS> vfs ) {
		this.vfs = vfs;
	}

	/**
	 * Gets the Cache.
	 *
	 * @return a specified Cache
	 */
	public Cache getCache() {
		return this.cache;
	}

	/**
	 * Sets the Cache.
	 *
	 * @param cache a Cache
	 */
	public void setCache( Cache cache ) {
		this.cache = cache;
	}

	/**
	 * Mybatis plugin list.
	 *
	 * @param plugins list of plugins
	 * @since mybatis-spring 1.0.1
	 */
	public void setPlugins( Interceptor ... plugins ) {
		this.plugins = plugins;
	}

	/**
	 * Packages to search for type aliases.
	 *
	 * <p>
	 * Since 2.0.1, allow to specify a wildcard such as {@code com.example.*.model}.
	 *
	 * @param typeAliasesPackage package to scan for domain objects
	 * @since mybatis-spring 1.0.1
	 */
	public void setTypeAliasesPackage( String typeAliasesPackage ) {
		this.typeAliasesPackage = typeAliasesPackage;
	}

	/**
	 * Super class which domain objects have to extend to have a type alias created. No effect if there is no package to scan configured.
	 *
	 * @param typeAliasesSuperType super class for domain objects
	 * @since mybatis-spring 1.1.2
	 */
	public void setTypeAliasesSuperType( Class<?> typeAliasesSuperType ) {
		this.typeAliasesSuperType = typeAliasesSuperType;
	}

	/**
	 * Packages to search for type handlers.
	 *
	 * <p>
	 * Since 2.0.1, allow to specify a wildcard such as {@code com.example.*.typehandler}.
	 *
	 * @param typeHandlersPackage package to scan for type handlers
	 * @since mybatis-spring 1.0.1
	 */
	public void setTypeHandlersPackage( String typeHandlersPackage ) {
		this.typeHandlersPackage = typeHandlersPackage;
	}

	/**
	 * Set type handlers. They must be annotated with {@code MappedTypes} and optionally with {@code MappedJdbcTypes}
	 *
	 * @param typeHandlers Type handler list
	 * @since mybatis-spring 1.0.1
	 */
	public void setTypeHandlers( TypeHandler<?> ... typeHandlers ) {
		this.typeHandlers = typeHandlers;
	}

	/**
	 * List of type aliases to register. They can be annotated with {@code Alias}
	 *
	 * @param typeAliases Type aliases list
	 * @since mybatis-spring 1.0.1
	 */
	public void setTypeAliases( Class<?> ... typeAliases ) {
		this.typeAliases = typeAliases;
	}

	/**
	 * Set the location of the MyBatis {@code SqlSessionFactory} config file. A typical value is 
	 * "WEB-INF/mybatis-configuration.xml".
	 *
	 * @param configLocation a location the MyBatis config file
	 */
	public void setConfigLocation( Resource configLocation ) {
		this.configLocation = configLocation;
	}

	/**
	 * Set a customized MyBatis configuration.
	 *
	 * @param configuration MyBatis configuration
	 * @since mybatis-spring 1.3.0
	 */
	public void setConfiguration( Configuration configuration ) {
		this.configuration = configuration;
	}

	/**
	 * Set locations of MyBatis mapper files that are going to be merged into the {@code SqlSessionFactory} configuration
	 * at runtime.
	 *
	 * This is an alternative to specifying "&lt;sqlmapper&gt;" entries in an MyBatis config file. This property being
	 * based on Spring's resource abstraction also allows for specifying resource patterns here: e.g.
	 * "classpath*:sqlmap/*-mapper.xml".
	 *
	 * @param mapperLocations location of MyBatis mapper files
	 */
	public void setMapperLocations( Resource ... mapperLocations ) {
		this.mapperLocations = mapperLocations;
	}

	/**
	 * Set optional properties to be passed into the SqlSession configuration, as alternative to a
	 * {@code &lt;properties&gt;} tag in the configuration xml file. This will be used to resolve placeholders in the
	 * config file.
	 *
	 * @param sqlSessionFactoryProperties optional properties for the SqlSessionFactory
	 */
	public void setConfigurationProperties( Properties sqlSessionFactoryProperties ) {
		this.configurationProperties = sqlSessionFactoryProperties;
	}

	/**
	 * Set the JDBC {@code DataSource} that this instance should manage transactions for. The {@code DataSource} should
	 * match the one used by the {@code SqlSessionFactory}: for example, you could specify the same JNDI DataSource for
	 * both.
	 *
	 * A transactional JDBC {@code Connection} for this {@code DataSource} will be provided to application code accessing
	 * this {@code DataSource} directly via {@code DataSourceUtils} or {@code DataSourceTransactionManager}.
	 *
	 * The {@code DataSource} specified here should be the target {@code DataSource} to manage transactions for, not a
	 * {@code TransactionAwareDataSourceProxy}. Only data access code may work with
	 * {@code TransactionAwareDataSourceProxy}, while the transaction manager needs to work on the underlying target
	 * {@code DataSource}. If there's nevertheless a {@code TransactionAwareDataSourceProxy} passed in, it will be
	 * unwrapped to extract its target {@code DataSource}.
	 *
	 * @param dataSource a JDBC {@code DataSource}
	 */
	public void setDataSource( DataSource dataSource ) {
		if ( dataSource instanceof TransactionAwareDataSourceProxy ) {
			// If we got a TransactionAwareDataSourceProxy, we need to perform
			// transactions for its underlying target DataSource, else data
			// access code won't see properly exposed transactions (i.e.
			// transactions for the target DataSource).
			this.dataSource = ( ( TransactionAwareDataSourceProxy ) dataSource ).getTargetDataSource();
		} else {
			this.dataSource = dataSource;
		}
	}

	/**
	 * Sets the {@code SqlSessionFactoryBuilder} to use when creating the {@code SqlSessionFactory}.
	 *
	 * This is mainly meant for testing so that mock SqlSessionFactory classes can be injected. By default,
	 * {@code SqlSessionFactoryBuilder} creates {@code DefaultSqlSessionFactory} instances.
	 *
	 * @param sqlSessionFactoryBuilder a SqlSessionFactoryBuilder
	 */
	public void setSqlSessionFactoryBuilder( SqlSessionFactoryBuilder sqlSessionFactoryBuilder ) {
		this.sqlSessionFactoryBuilder = sqlSessionFactoryBuilder;
	}

	/**
	 * Set the MyBatis TransactionFactory to use. Default is {@code SpringManagedTransactionFactory}
	 *
	 * The default {@code SpringManagedTransactionFactory} should be appropriate for all cases: be it Spring transaction
	 * management, EJB CMT or plain JTA. If there is no active transaction, SqlSession operations will execute SQL
	 * statements non-transactionally.
	 *
	 * <b>It is strongly recommended to use the default {@code TransactionFactory}.</b> If not used, any attempt at
	 * getting an SqlSession through Spring's MyBatis framework will throw an exception if a transaction is active.
	 * 
	 * @see SpringManagedTransactionFactory
	 * @param transactionFactory the MyBatis TransactionFactory
	 */
	public void setTransactionFactory( TransactionFactory transactionFactory ) {
		this.transactionFactory = transactionFactory;
	}

	/**
	 * <b>NOTE:</b> This class <em>overrides</em> any {@code Environment} you have set in the MyBatis config file. This is
	 * used only as a placeholder name. The default value is {@code SqlSessionFactoryBean.class.getSimpleName()}.
	 *
	 * @param environment the environment name
	 */
	public void setEnvironment( String environment ) {
		this.environment = environment;
	}

	/**
	 * Set scripting language drivers.
	 *
	 * @param scriptingLanguageDrivers scripting language drivers
	 * @since mybatis-spring 2.0.2
	 */
	public void setScriptingLanguageDrivers( LanguageDriver ... scriptingLanguageDrivers ) {
		this.scriptingLanguageDrivers = scriptingLanguageDrivers;
	}

	/**
	 * Set a default scripting language driver class.
	 *
	 * @param defaultScriptingLanguageDriver A default scripting language driver class
	 * @since mybatis-spring 2.0.2
	 */
	public void setDefaultScriptingLanguageDriver( Class<? extends LanguageDriver> defaultScriptingLanguageDriver ) {
		this.defaultScriptingLanguageDriver = this.enableXmlSyntaxParsing ? MyBatisMapperLanguageDriver.class : defaultScriptingLanguageDriver;
	}
	
	/**
	 * Whether to enable log printing
	 * 
	 * @param enableLogger whether to print the log
	 * @since mybatis-mapper-spring 1.3.0
	 */
	public void setEnableLogger( boolean enableLogger ) {
		initConfigurationProperties();
		this.configurationProperties.put( "enableLogger", enableLogger );
	}

	/**
	 * Whether to enable mapper scan log
	 * 
	 * @param enableMapperScanLog whether to enable mapper scan log
	 * @since mybatis-mapper-spring 1.3.0
	 */
	public void setEnableMapperScanLog( boolean enableMapperScanLog ) {
		initConfigurationProperties();
		this.configurationProperties.put( "enableMapperScanLog", enableMapperScanLog );
	}
	
	/**
	 * Whether to enable runtime logs
	 * 
	 * @param enableCompilationLog whether to enable runtime logs
	 * @since mybatis-mapper-spring 1.3.0
	 */
	public void setEnableRuntimeLog( boolean enableRuntimeLog ) {
		initConfigurationProperties();
		this.configurationProperties.put( "enableRuntimeLog", enableRuntimeLog );
	}
	
	/**
	 * Whether to enable compilation log
	 * 
	 * @param enableCompilationLog whether to enable compilation log
	 * @since mybatis-mapper-spring 1.3.0
	 */
	public void setEnableCompilationLog( boolean enableCompilationLog ) {
		initConfigurationProperties();
		this.configurationProperties.put( "enableCompilationLog", enableCompilationLog );
	}

	/**
	 * Whether to enable keyword to uppercase configuration
	 * 
	 * @param enableKeywordsToUppercase whether to convert to uppercase keywords
	 * @since mybatis-mapper-spring 1.3.0 
	 */
	public void setEnableKeywordsToUppercase( boolean enableKeywordsToUppercase ) {
		initConfigurationProperties();
		this.configurationProperties.put( "enableKeywordsToUppercase", enableKeywordsToUppercase );
	}

	/**
	 * Set the database column style
	 * 
	 * @param databaseColumnStyle the database column style
	 * @since mybatis-mapper-spring 1.3.0
	 */
	public void setDatabaseColumnStyle( String databaseColumnStyle ) {
		initConfigurationProperties();
		this.configurationProperties.put( "databaseColumnStyle", databaseColumnStyle );
	}

	/**
	 * Whether to enable xml syntax parsing
	 * 
	 * @param enableXmlSyntaxParsing whether enable xml syntax parsing
	 * @since mybatis-mapper-spring 1.3.0
	 */
	public void setEnableXmlSyntaxParsing( boolean enableXmlSyntaxParsing ) {
		initConfigurationProperties();
		this.enableXmlSyntaxParsing = enableXmlSyntaxParsing;
		this.defaultScriptingLanguageDriver = MyBatisMapperLanguageDriver.class;
		this.configurationProperties.put( "enableXmlSyntaxParsing", enableXmlSyntaxParsing );
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void afterPropertiesSet() throws Exception {
		notNull( dataSource, "Property 'dataSource' is required" );
		notNull( sqlSessionFactoryBuilder, "Property 'sqlSessionFactoryBuilder' is required" );
		state( ( configuration == null && configLocation == null ) || ! ( configuration != null && configLocation != null ),
				"Property 'configuration' and 'configLocation' can not specified with together" );
		this.sqlSessionFactory = buildSqlSessionFactory();
	}

	/**
	 * Build a {@code SqlSessionFactory} instance.
	 *
	 * The default implementation uses the standard MyBatis {@code XMLConfigBuilder} API to build a
	 * {@code SqlSessionFactory} instance based on an Reader. Since 1.3.0, it can be specified a {@link Configuration}
	 * instance directly(without config file).
	 *
	 * @return SqlSessionFactory
	 * @throws Exception if configuration is failed
	 */
	protected SqlSessionFactory buildSqlSessionFactory() throws Exception {
		final Configuration targetConfiguration;

		XMLConfigBuilder xmlConfigBuilder = null;
		if ( this.configuration != null ) {
			targetConfiguration = this.configuration;
			if ( targetConfiguration.getVariables() == null ) {
				targetConfiguration.setVariables( this.configurationProperties );
			} else if ( this.configurationProperties != null ) {
				targetConfiguration.getVariables().putAll( this.configurationProperties );
			}
		} else if ( this.configLocation != null ) {
			xmlConfigBuilder = new XMLConfigBuilder( this.configLocation.getInputStream(), null, this.configurationProperties );
			targetConfiguration = xmlConfigBuilder.getConfiguration();
		} else {
			LOGGER.debug( () -> "Property 'configuration' or 'configLocation' not specified, using default MyBatis Configuration" );
			targetConfiguration = new Configuration();
			Optional.ofNullable( this.configurationProperties ).ifPresent( targetConfiguration::setVariables );
		}

		Optional.ofNullable( this.objectFactory ).ifPresent( targetConfiguration::setObjectFactory );
		Optional.ofNullable( this.objectWrapperFactory ).ifPresent( targetConfiguration::setObjectWrapperFactory );
		Optional.ofNullable( this.vfs ).ifPresent( targetConfiguration::setVfsImpl );

		if ( hasLength( this.typeAliasesPackage ) ) {
			scanClasses( this.typeAliasesPackage, this.typeAliasesSuperType ).stream()
				.filter( clazz -> !clazz.isAnonymousClass() ).filter( clazz -> !clazz.isInterface() )
				.filter( clazz -> !clazz.isMemberClass() )
				.forEach( targetConfiguration.getTypeAliasRegistry()::registerAlias );
		}

		if ( !isEmpty( this.typeAliases ) ) {
			Stream.of( this.typeAliases ).forEach( typeAlias -> {
				targetConfiguration.getTypeAliasRegistry().registerAlias( typeAlias );
				LOGGER.debug( () -> "Registered type alias: '" + typeAlias + "'" );
			});
		}

		if ( !isEmpty( this.plugins ) ) {
			Stream.of( this.plugins ).forEach( plugin -> {
				targetConfiguration.addInterceptor( plugin );
				LOGGER.debug( () -> "Registered plugin: '" + plugin + "'" );
			});
		}

		if ( hasLength( this.typeHandlersPackage ) ) {
			scanClasses( this.typeHandlersPackage, TypeHandler.class ).stream()
				.filter( clazz -> !clazz.isAnonymousClass() ).filter( clazz -> !clazz.isInterface() )
				.filter( clazz -> !Modifier.isAbstract( clazz.getModifiers() ) )
				.forEach( targetConfiguration.getTypeHandlerRegistry()::register );
		}

		if ( !isEmpty( this.typeHandlers ) ) {
			Stream.of( this.typeHandlers ).forEach( typeHandler -> {
				targetConfiguration.getTypeHandlerRegistry().register( typeHandler );
				LOGGER.debug( () -> "Registered type handler: '" + typeHandler + "'" );
			});
		}

		if ( !isEmpty( this.scriptingLanguageDrivers ) ) {
			Stream.of( this.scriptingLanguageDrivers ).forEach( languageDriver -> {
				targetConfiguration.getLanguageRegistry().register( languageDriver );
				LOGGER.debug( () -> "Registered scripting language driver: '" + languageDriver + "'" );
			});
		}
		
		// ↓↓ Original location ↓↓
		// Optional.ofNullable( this.defaultScriptingLanguageDriver ).ifPresent( targetConfiguration::setDefaultScriptingLanguage );

		if ( this.databaseIdProvider != null ) { // fix #64 set databaseId before parse mapper xmls
			try {
				targetConfiguration.setDatabaseId( this.databaseIdProvider.getDatabaseId( this.dataSource ) );
			} catch ( SQLException e ) {
				throw new NestedIOException( "Failed getting a databaseId", e );
			}
		}
		Optional.ofNullable( this.cache ).ifPresent( targetConfiguration::addCache );

		if ( xmlConfigBuilder != null ) {
			try {
				xmlConfigBuilder.parse();
				LOGGER.debug( () -> "Parsed configuration file: '" + this.configLocation + "'" );
			} catch ( Exception ex ) {
				throw new NestedIOException( "Failed to parse config resource: " + this.configLocation, ex );
			} finally {
				ErrorContext.instance().reset();
			}
		}
		
		// ↑↑ Changed position ↑↑
		// Changed the order, nothing else changed.
		// Mainly to prevent the XMLConfigBuilder from changing the default language driver.
		Optional.ofNullable( this.defaultScriptingLanguageDriver ).ifPresent( targetConfiguration::setDefaultScriptingLanguage );
		
		targetConfiguration.setEnvironment( new Environment( this.environment,
				this.transactionFactory == null ? new SpringManagedTransactionFactory() : this.transactionFactory,
				this.dataSource ) );

		if ( this.mapperLocations != null ) {
			if ( this.mapperLocations.length == 0 ) {
				LOGGER.warn( () -> "Property 'mapperLocations' was specified but matching resources are not found." );
			} else {
				for ( Resource mapperLocation : this.mapperLocations ) {
					if ( mapperLocation == null ) {
						continue;
					}
					try {
						XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder( mapperLocation.getInputStream(),
								targetConfiguration, mapperLocation.toString(), targetConfiguration.getSqlFragments() );
						xmlMapperBuilder.parse();
					} catch ( Exception e ) {
						throw new NestedIOException( "Failed to parse mapping resource: '" + mapperLocation + "'", e );
					} finally {
						ErrorContext.instance().reset();
					}
					LOGGER.debug( () -> "Parsed mapper file: '" + mapperLocation + "'" );
				}
			}
		} else {
			LOGGER.debug( () -> "Property 'mapperLocations' was not specified." );
		}
		return this.sqlSessionFactoryBuilder.build( targetConfiguration );
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public SqlSessionFactory getObject() throws Exception {
		if ( this.sqlSessionFactory == null ) {
			afterPropertiesSet();
		}
		return this.sqlSessionFactory;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<?> getObjectType() {
		return this.sqlSessionFactory == null ? SqlSessionFactory.class : this.sqlSessionFactory.getClass();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isSingleton() {
		return true;
	}

	/**
	 * Add mybatis-mapper plugin to refactor the basic functionality of mybatis
	 * 
	 * @since mybatis-mapper-spring 1.3.0
	 */
	@Override
	public void onApplicationEvent( ApplicationEvent event ) {
		if ( event instanceof ContextRefreshedEvent ) {
			LoggerUtil.printBootstrapLog();
			this.mybatisMapperBuilder.refactoring( sqlSessionFactory.getConfiguration() );
			LoggerUtil.printLoadedLog();
		}
	}
	
	/**
	 * Mybatis-mapper auxiliary method
	 * 
	 * @since mybatis-mapper-spring 1.3.0
	 */
	private void initConfigurationProperties() {
		if ( configurationProperties == null ) {
			this.configurationProperties = new Properties();
		}
	}
	
	private Set<Class<?>> scanClasses( String packagePatterns, Class<?> assignableType ) throws IOException {
		Set<Class<?>> classes = new HashSet<>();
		String [] packagePatternArray = tokenizeToStringArray( packagePatterns, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS );
		for ( String packagePattern : packagePatternArray ) {
			Resource [] resources = RESOURCE_PATTERN_RESOLVER.getResources( 
				ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + ClassUtils.convertClassNameToResourcePath( packagePattern ) + "/**/*.class" 
			);
			for ( Resource resource : resources ) {
				try {
					ClassMetadata classMetadata = METADATA_READER_FACTORY.getMetadataReader( resource ).getClassMetadata();
					Class<?> clazz = Resources.classForName( classMetadata.getClassName() );
					if ( assignableType == null || assignableType.isAssignableFrom( clazz ) ) {
						classes.add( clazz );
					}
				} catch ( Throwable e ) {
					LOGGER.warn( () -> "Cannot load the '" + resource + "'. Cause by " + e.toString() );
				}
			}
		}
		return classes;
	}

}

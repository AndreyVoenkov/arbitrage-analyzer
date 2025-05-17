package org.example.moexarbitrage;


import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@PropertySource("classpath:application.properties")
//@EnableTransactionManagement
@ComponentScan(basePackages = "org.example.moexarbitrage")
@EnableJpaRepositories

public class AppConfig {

    // 1. Настройка DataSource для PostgreSQL с использованием HikariCP
    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5439/moexdb");
        dataSource.setUsername("postgres");
        dataSource.setPassword("root");
        dataSource.setAutoCommit(false); // Отключаем автокоммит
        return dataSource;
    }

    // 2. Настройка фабрики сессий Hibernate
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("org.example.moexarbitrage.model");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaProperties(hibernateProperties());
        return factory;
    }

    // 3. Настройка транзакционного менеджера
    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        return transactionManager;
    }

    // 4. Свойства Hibernate
    public Properties hibernateProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.show_sql", "true");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // properties.setProperty("hibernate.format_sql", "true");
        properties.setProperty("hibernate.hbm2ddl.auto", "update"); // update / create / validate
        properties.setProperty("hibernate.use_sql_comments", "false");
        properties.setProperty("hibernate.connection.autocommit", "false"); // Отключаем автокоммит
        properties.setProperty("hibernate.connection.provider_disables_autocommit", "true"); // Отключаем автокоммит
        properties.setProperty("hibernate.jdbc.batch_size", "20"); // Оптимизация для пакетной обработки
        properties.setProperty("hibernate.order_inserts", "true"); // Оптимизация для пакетной обработки
        properties.setProperty("hibernate.order_updates", "true"); // Оптимизация для пакетной обработки
        return properties;
    }

}

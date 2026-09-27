package com.bifrost.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class ConnectionPool {

    private final static int MAXIMUM_POOL_SIZE =10;
    private final static int CONNECTION_TIMEOUT=30000;

    private final HikariDataSource hikariDataSource;
    //setters
    public ConnectionPool(DataSource dataSource){
        HikariConfig config = new HikariConfig();

        config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT);
        config.setDataSource(dataSource);

        this.hikariDataSource = new HikariDataSource(config);

    }
    public HikariDataSource getHikariDataSource(){
        return hikariDataSource;
    }


}

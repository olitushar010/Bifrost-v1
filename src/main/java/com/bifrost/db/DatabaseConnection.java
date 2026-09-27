package com.bifrost.db;

import java.sql.Connection;
import java.sql.SQLException;
import com.bifrost.config.DbConfig;
import com.bifrost.config.ConnectionPool;
import org.postgresql.ds.PGSimpleDataSource;


// HickariCP is a connection pool for JDBC
public class DatabaseConnection {

    private static final String HOST = DbConfig.getHost();
    private static final int PORT = DbConfig.getPort();
    private static final String DATABASE = DbConfig.getDatabase();
    private static final String USER = DbConfig.getUser();
    private static final String PASSWORD = DbConfig.getPassword();

    private static final PGSimpleDataSource pgSimpleDataSource = createDataSource();

   private static PGSimpleDataSource createDataSource(){
       PGSimpleDataSource ds = new PGSimpleDataSource();
       ds.setServerNames(new String[]{HOST});
       ds.setPortNumbers(new int[]{PORT});
       ds.setDatabaseName(DATABASE);
       ds.setUser(USER);
       ds.setPassword(PASSWORD);
       return ds;
   }
   private static final ConnectionPool connectionPool = new ConnectionPool(pgSimpleDataSource);

   public static Connection getConnection() throws SQLException {
       return connectionPool.getHikariDataSource().getConnection();
   }

}




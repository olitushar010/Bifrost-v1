package com.bifrost.config;
import io.github.cdimascio.dotenv.Dotenv;


public class DbConfig {
    //Config
    private static final Dotenv ENV = Dotenv.load();

    private static final String HOST = getRequired(ENV,"DB_HOST");
    private static final int PORT = getRequiredInt(ENV,"DB_PORT");
    private static final String USER = getRequired(ENV,"DB_USER");
    private static final String PASSWORD = getRequired(ENV,"DB_PASSWORD");

    private static final String DATABASE = getRequired(ENV,"DB_NAME");

    //Getter

    public static String getHost() {
        return HOST;
    }

    public static int getPort() {
        return PORT;
    }

    public static String getUser() {
        return USER;
    }

    public static String getPassword() {
        return PASSWORD;
    }

    public static String getDatabase() {
        return DATABASE;
    }
    public static String getRequired(Dotenv env, String key) {
        String value = env.get(key);
        if(value == null){
            throw new IllegalStateException("Required environment variable not found: " + key);
        }
        return value;
    }
    public static int getRequiredInt(Dotenv env, String key) {
        String value = getRequired(env, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Environment variable is not a valid integer: " + key);
        }
    }
}

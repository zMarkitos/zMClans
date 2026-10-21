package dev.zm.clan.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.zm.clan.config.ConfigManager;
import dev.zm.clan.zMClans;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Manages the HikariCP connection pool for SQLite or MySQL.
 * All SQL is executed through {@link #getConnection()}.
 */
public final class DatabaseManager {

    private final zMClans plugin;
    private HikariDataSource dataSource;

    /**
     * Schema version stored in {@code schema_version}.
     * Bump this when adding a migration inside {@link #runMigrations()}.
     */
    private static final int CURRENT_SCHEMA_VERSION = 2;

    public DatabaseManager(zMClans plugin) {
        this.plugin = plugin;
    }

    private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "zMClans-Database-Writer");
        thread.setDaemon(true);
        return thread;
    });
    private boolean acceptingWrites = true;

    /** Establishes the connection pool. @return {@code true} if successful. */
    public boolean connect() {
        ConfigManager cfg = plugin.getConfigManager();
        HikariConfig hc = new HikariConfig();

        if ("MYSQL".equalsIgnoreCase(cfg.getStorageType())) {
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setJdbcUrl(
                    String.format("jdbc:mysql://%s:%d/%s?useSSL=%b&allowPublicKeyRetrieval=%b&characterEncoding=utf8",
                            cfg.getMysqlHost(), cfg.getMysqlPort(), cfg.getMysqlDatabase(), cfg.isMysqlSsl(),
                            cfg.isMysqlAllowPublicKeyRetrieval()));
            hc.setUsername(cfg.getMysqlUser());
            hc.setPassword(cfg.getMysqlPassword());
            hc.setMaximumPoolSize(cfg.getMysqlPoolSize());
        } else {
            // SQLite
            File dbFile = new File(plugin.getDataFolder(), cfg.getSqliteFile());
            dbFile.getParentFile().mkdirs();
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hc.setMaximumPoolSize(1); // SQLite is single-writer
            hc.setConnectionTestQuery("SELECT 1");
        }

        hc.setPoolName("zMClans-Pool");
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(hc);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    plugin.getMessages().get("db-error", "error", e.getMessage()), e);
            return false;
        }
    }

    /**
     * Creates the schema on a fresh install and applies any pending migrations
     * to existing installations.
     * Schema changes are applied additively so existing clan and member rows
     * are preserved.
     */
    public void runMigrations() {
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {

            int currentVersion = readSchemaVersion(stmt);

            if (currentVersion < 1) {
                createBaseSchema(stmt);
            }

            migrateClanOptionsAndStatistics(con);

            writeSchemaVersion(stmt, Math.max(currentVersion, CURRENT_SCHEMA_VERSION));

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Migration failed; startup will be aborted to avoid loading an incompatible schema.", e);
            throw new IllegalStateException("Could not migrate the zMClans database.", e);
        }
    }

    private void migrateClanOptionsAndStatistics(Connection con) throws SQLException {
        ensureColumn(con, "clans", "ally_homes", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(con, "clans", "ally_chat", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn(con, "clans", "ally_pvp", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(con, "clans", "public_join", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(con, "clans", "allies", "VARCHAR(2048) DEFAULT ''");
        ensureColumn(con, "clans", "rivals", "VARCHAR(2048) DEFAULT ''");
        ensureColumn(con, "clans", "kills", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn(con, "clans", "deaths", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn(con, "clans", "level", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn(con, "clan_members", "kills", "BIGINT NOT NULL DEFAULT 0");
        ensureColumn(con, "clan_members", "deaths", "BIGINT NOT NULL DEFAULT 0");
        if ("MYSQL".equalsIgnoreCase(plugin.getConfigManager().getStorageType())) {
            ensureMySqlBigInt(con, "clans", "points");
            ensureMySqlBigInt(con, "clans", "kills");
            ensureMySqlBigInt(con, "clans", "deaths");
        }
    }

    private void ensureColumn(Connection con, String table, String column, String definition) throws SQLException {
        try (var columns = con.getMetaData().getColumns(null, null, table, column)) {
            if (columns.next())
                return;
        }
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void ensureMySqlBigInt(Connection con, String table, String column) throws SQLException {
        try (var columns = con.getMetaData().getColumns(null, null, table, column)) {
            if (columns.next() && columns.getInt("DATA_TYPE") == java.sql.Types.BIGINT)
                return;
        }
        try (Statement statement = con.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN " + column + " BIGINT NOT NULL DEFAULT 0");
        }
    }

    public synchronized CompletableFuture<Void> executeAsync(String operation, Runnable action) {
        if (!acceptingWrites) {
            IllegalStateException error = new IllegalStateException("Database writer is shutting down.");
            plugin.getLogger().log(Level.SEVERE, "Rejected database operation: " + operation, error);
            return CompletableFuture.failedFuture(error);
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(action, writeExecutor);
        future.whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Database operation failed: " + operation, error);
            }
        });
        return future;
    }

    public boolean awaitPendingWrites() {
        CompletableFuture<Void> barrier;
        synchronized (this) {
            if (!acceptingWrites)
                return false;
            barrier = CompletableFuture.runAsync(() -> {
            }, writeExecutor);
        }
        return await(barrier, "pending database writes");
    }

    public synchronized boolean stopAsyncWritesAndAwait() {
        acceptingWrites = false;
        CompletableFuture<Void> barrier;
        try {
            barrier = CompletableFuture.runAsync(() -> {
            }, writeExecutor);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not queue the database shutdown barrier.", e);
            writeExecutor.shutdown();
            return false;
        }
        boolean flushed = await(barrier, "database shutdown flush");
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Database writer did not stop within 60 seconds; pending writes may remain.");
                writeExecutor.shutdownNow();
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "Interrupted while stopping the database writer.", e);
            writeExecutor.shutdownNow();
            return false;
        }
        return flushed;
    }

    private boolean await(CompletableFuture<Void> future, String operation) {
        try {
            future.get(60, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "Interrupted while waiting for " + operation + ".", e);
        } catch (java.util.concurrent.ExecutionException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed while waiting for " + operation + ".", e.getCause());
        } catch (TimeoutException e) {
            plugin.getLogger().log(Level.SEVERE, "Timed out while waiting for " + operation + ".", e);
        }
        return false;
    }

    /** Creates every table in its latest form for a fresh installation. */
    private void createBaseSchema(Statement stmt) throws SQLException {
        stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clans (
                        id            VARCHAR(36) PRIMARY KEY,
                        name          VARCHAR(191) NOT NULL UNIQUE,
                        tag           VARCHAR(191) NOT NULL UNIQUE,
                        description   VARCHAR(2048) NOT NULL DEFAULT '',
                        leader        VARCHAR(36) NOT NULL,
                        leader_rank   VARCHAR(64) NOT NULL DEFAULT 'leader',
                        friendly_fire INTEGER NOT NULL DEFAULT 0,
                        open_invite   INTEGER NOT NULL DEFAULT 0,
                        home          TEXT DEFAULT NULL,
                        points        BIGINT NOT NULL DEFAULT 0,
                        created_at    BIGINT NOT NULL,
                        ally_homes    INTEGER NOT NULL DEFAULT 0,
                        ally_chat     INTEGER NOT NULL DEFAULT 1,
                        ally_pvp      INTEGER NOT NULL DEFAULT 0,
                        public_join   INTEGER NOT NULL DEFAULT 0,
                        allies        VARCHAR(2048) DEFAULT '',
                        rivals        VARCHAR(2048) DEFAULT '',
                        kills         BIGINT NOT NULL DEFAULT 0,
                        deaths        BIGINT NOT NULL DEFAULT 0,
                        level         INTEGER NOT NULL DEFAULT 1
                    )
                """);

        stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS clan_members (
                        player_uuid VARCHAR(36) NOT NULL,
                        clan_id     VARCHAR(36) NOT NULL,
                        rank_id     VARCHAR(64) NOT NULL DEFAULT 'member',
                        joined_at   BIGINT NOT NULL,
                        kills       BIGINT NOT NULL DEFAULT 0,
                        deaths      BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (player_uuid),
                        FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE
                    )
                """);

        plugin.getLogger().info(
                plugin.getMessages().get("db-migration", "version", String.valueOf(CURRENT_SCHEMA_VERSION)));
    }

    /** Reads the current schema version (0 on a fresh install). */
    private int readSchemaVersion(Statement stmt) throws SQLException {
        stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER NOT NULL
                    )
                """);
        try (var rs = stmt.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Persists the schema version, replacing any previous row. */
    private void writeSchemaVersion(Statement stmt, int version) throws SQLException {
        stmt.executeUpdate("DELETE FROM schema_version");
        stmt.executeUpdate("INSERT INTO schema_version VALUES (" + version + ")");
    }

    /**
     * Returns a connection from the pool. Callers must close it in a
     * try-with-resources.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not available.");
        }
        return dataSource.getConnection();
    }

    /** Gracefully shuts down the connection pool. */
    public void shutdown() {
        if (!writeExecutor.isShutdown()) {
            stopAsyncWritesAndAwait();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
package me.chunklock.services;

import me.chunklock.ChunklockPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pricing storage in the same MySQL database the server already uses for chunk and player
 * data, when {@code database.type: mysql} (#95).
 *
 * <p>Shares {@link MySqlConnectionProvider} with {@link MySqlChunkDatabase} rather than
 * opening a second pool: one pool, one set of credentials, one thing to size. Connections
 * come from that pool and <strong>must</strong> be returned, so {@link #borrow()} yields a
 * {@link BorrowedConnection#pooled} whose close hands it back. Leaking them exhausts the pool
 * and hangs the server, which is why borrowing is the only way callers get a connection.</p>
 *
 * <p>This is what makes a network behave: every node reads the same committed prices (#83)
 * and the same material baseline (#86), instead of each keeping a private H2 file.</p>
 */
public class MySqlCostStorageBackend implements CostStorageBackend {

    private final ChunklockPlugin plugin;
    private final MySqlConnectionProvider connectionProvider;
    private boolean available;

    public MySqlCostStorageBackend(ChunklockPlugin plugin, MySqlConnectionProvider connectionProvider) {
        this.plugin = plugin;
        this.connectionProvider = connectionProvider;
    }

    @Override
    public boolean initialize() {
        if (connectionProvider == null) {
            plugin.getLogger().severe("MySQL pricing storage selected without a connection provider");
            return false;
        }

        SqlDialect dialect = dialect();
        try (Connection connection = connectionProvider.getConnection();
             Statement stmt = connection.createStatement()) {

            stmt.execute(dialect.createChunkCostsTable());
            stmt.execute(dialect.createChunkProfilesTable());
            stmt.execute(dialect.createBaselineTable());
            for (String index : dialect.createIndexes()) {
                stmt.execute(index);
            }

            available = true;
            plugin.getLogger().info("Pricing storage initialized: MySQL");
            return true;

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize MySQL pricing storage: " + e.getMessage());
            available = false;
            return false;
        }
    }

    @Override
    public BorrowedConnection borrow() throws SQLException {
        if (!available) {
            throw new SQLException("MySQL pricing storage not initialized");
        }
        return BorrowedConnection.pooled(connectionProvider.getConnection());
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public SqlDialect dialect() {
        return SqlDialect.MYSQL;
    }

    @Override
    public String describe() {
        return "MySQL";
    }

    @Override
    public void close() {
        // The pool belongs to StorageFactory's selection, which closes it alongside the chunk
        // and player stores. Closing it here would pull it out from under them.
        available = false;
    }
}

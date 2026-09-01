package me.chunklock.services;

import me.chunklock.ChunklockPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Pricing storage in a local H2 file. The default, and what every server ran before #95.
 *
 * <p>Holds a single long-lived connection rather than a pool, which is why
 * {@link #borrow()} returns a {@link BorrowedConnection#shared} - callers use
 * try-with-resources as they would for any backend, and closing is a no-op here so the
 * connection survives for the next caller.</p>
 */
public class H2CostStorageBackend implements CostStorageBackend {

    private final ChunklockPlugin plugin;
    private final File databaseFile;
    private Connection connection;

    public H2CostStorageBackend(ChunklockPlugin plugin) {
        this.plugin = plugin;
        // H2 appends .mv.db itself, so this is the base name rather than the actual file.
        this.databaseFile = new File(plugin.getDataFolder(), "chunk_costs");
    }

    @Override
    public boolean initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            loadDriver();

            String url = "jdbc:h2:file:" + databaseFile.getAbsolutePath() + ";AUTO_SERVER=TRUE";
            connection = DriverManager.getConnection(url);

            createTables();

            plugin.getLogger().info("Pricing storage initialized: H2 file " + databaseFile.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize H2 pricing storage: " + e.getMessage());
            e.printStackTrace();
            connection = null;
            return false;
        }
    }

    /**
     * Load the H2 driver explicitly.
     *
     * <p>Needed because the shade plugin relocates {@code org.h2} to
     * {@code me.chunklock.libs.h2}, so the driver is not on the class path under its usual
     * name in a built jar. Tests run unrelocated, hence both attempts.</p>
     */
    private void loadDriver() throws SQLException {
        try {
            Class.forName("me.chunklock.libs.h2.Driver");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("org.h2.Driver");
            } catch (ClassNotFoundException e2) {
                plugin.getLogger().warning("H2 driver class not found in either location");
                throw new SQLException("H2 JDBC driver not found", e2);
            }
        }
    }

    private void createTables() throws SQLException {
        SqlDialect dialect = dialect();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(dialect.createChunkCostsTable());
            stmt.execute(dialect.createChunkProfilesTable());
            stmt.execute(dialect.createBaselineTable());
            for (String index : dialect.createIndexes()) {
                stmt.execute(index);
            }
        }
    }

    @Override
    public BorrowedConnection borrow() throws SQLException {
        if (connection == null) {
            throw new SQLException("H2 pricing storage not initialized");
        }
        return BorrowedConnection.shared(connection);
    }

    @Override
    public boolean isAvailable() {
        return connection != null;
    }

    @Override
    public SqlDialect dialect() {
        return SqlDialect.H2;
    }

    @Override
    public String describe() {
        return "H2";
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Closed H2 pricing storage connection");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing pricing storage: " + e.getMessage());
        } finally {
            connection = null;
        }
    }
}

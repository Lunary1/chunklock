package me.chunklock.services;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Where pricing data lives: chunk costs (#83) and chunk profiles plus the material
 * baseline (#86).
 *
 * <h3>Why this exists (#95)</h3>
 *
 * <p>{@link ChunkCostDatabase} used to open its own H2 file unconditionally, never consulting
 * {@link StorageFactory} or {@code database.type}. A server configured for MySQL therefore ran
 * <em>two</em> backends: MySQL for chunk and player data, H2 for everything pricing-related.
 * On a single server that is merely untidy. On a network it breaks the features outright -
 * each node keeps its own H2 file, so committed prices diverge per node and a player sees a
 * different price for the same chunk depending which server they log into, contradicting the
 * premise of #83. The #86 material baseline is global world state, so per-node baselines mean
 * the same chunk scores differently on different nodes.</p>
 *
 * <h3>Two lifecycles, deliberately not unified</h3>
 *
 * <p>The two implementations differ in a way callers must respect: H2 holds one long-lived
 * connection that must <strong>not</strong> be closed by callers, while MySQL hands out pooled
 * connections that <strong>must</strong> be closed to return them to the pool. Rather than
 * make callers know which they have, {@link #borrow()} always returns a
 * {@link BorrowedConnection} that is safe to close in try-with-resources: closing releases a
 * pooled connection and leaves a shared one open.</p>
 *
 * <p>That is the whole reason this is an interface over a connection rather than a shared
 * {@code Connection}. Getting it wrong in either direction is silent: closing H2's shared
 * connection kills all later persistence, and leaking pooled MySQL connections exhausts the
 * pool and hangs the server.</p>
 */
public interface CostStorageBackend {

    /**
     * Open the backend and create the tables pricing needs.
     *
     * @return true when the backend is usable; false leaves the plugin running without
     *         pricing persistence rather than failing startup
     */
    boolean initialize();

    /**
     * Borrow a connection for one unit of work.
     *
     * <p>Always use in try-with-resources. Whether closing actually closes the underlying
     * connection is the backend's business, not the caller's.</p>
     *
     * @throws SQLException if the backend is unavailable
     */
    BorrowedConnection borrow() throws SQLException;

    /** Whether the backend initialized and can serve connections. */
    boolean isAvailable();

    /** The SQL dialect for statements that differ between H2 and MySQL. */
    SqlDialect dialect();

    /** A short name for logs, e.g. {@code "H2"} or {@code "MySQL"}. */
    String describe();

    /** Release the backend. Safe to call when never initialized. */
    void close();

    /**
     * A connection that is safe to close regardless of which backend produced it.
     *
     * <p>Not a {@code Connection} subtype on purpose: implementing that interface means
     * delegating every method, and the only behaviour that needs to vary is closing.</p>
     */
    final class BorrowedConnection implements AutoCloseable {

        private final Connection connection;
        private final boolean closeOnRelease;

        private BorrowedConnection(Connection connection, boolean closeOnRelease) {
            this.connection = connection;
            this.closeOnRelease = closeOnRelease;
        }

        /** A connection borrowed from a pool; closing returns it to the pool. */
        public static BorrowedConnection pooled(Connection connection) {
            return new BorrowedConnection(connection, true);
        }

        /** A long-lived shared connection; closing is a no-op so it survives the caller. */
        public static BorrowedConnection shared(Connection connection) {
            return new BorrowedConnection(connection, false);
        }

        public Connection get() {
            return connection;
        }

        @Override
        public void close() throws SQLException {
            if (closeOnRelease && connection != null && !connection.isClosed()) {
                connection.close();
            }
        }
    }
}

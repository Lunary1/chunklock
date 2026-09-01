package me.chunklock.services;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The statements that genuinely differ between H2 and MySQL for pricing storage (#95).
 *
 * <h3>Why routing needed more than a connection swap</h3>
 *
 * <p>Pointing {@link ChunkCostDatabase} at a MySQL connection is not enough, because the SQL
 * it runs is H2-specific and would fail on every write:</p>
 *
 * <ul>
 *   <li><strong>{@code MERGE ... KEY (cols)}</strong> is an H2 extension. MySQL has no
 *       {@code MERGE} statement at all - it spells an upsert
 *       {@code INSERT ... ON DUPLICATE KEY UPDATE}, which is what the existing
 *       {@link MySqlChunkDatabase} already uses.</li>
 *   <li><strong>The baseline upsert reads the table it writes.</strong> H2 accepts a
 *       correlated subquery against the merge target; MySQL rejects it with
 *       <em>"You can't specify target table for update in FROM clause"</em>. MySQL expresses
 *       the same accumulate-in-place with {@code VALUES()} in the update clause instead.</li>
 *   <li><strong>Index creation.</strong> H2 supports {@code CREATE INDEX IF NOT EXISTS};
 *       MySQL does not, so indexes are declared inline in {@code CREATE TABLE} where they
 *       are idempotent alongside {@code CREATE TABLE IF NOT EXISTS}.</li>
 *   <li><strong>String concatenation.</strong> {@code ||} is concatenation in H2 but a
 *       logical OR in MySQL's default mode, where a chunk key would silently evaluate to
 *       {@code 0} - collapsing every chunk to one key and returning a distinct count of 1.
 *       MySQL needs {@code CONCAT}. This one is worth flagging: it fails
 *       <em>silently and wrongly</em> rather than throwing, which is exactly how #90 hid for
 *       months.</li>
 * </ul>
 *
 * <p>Keeping these as named statements rather than scattering {@code if (mysql)} branches
 * through the stores means the two engines' SQL sits side by side, where a difference is
 * visible instead of implied.</p>
 *
 * <h3>The #90 rule applies to both</h3>
 *
 * <p>Every upsert here keys on a real unique constraint. H2 says so explicitly with a
 * {@code KEY} clause; MySQL infers it from the unique index named in the table definition.
 * If a table's constraint is ever changed, both variants have to change with it - which is
 * why {@code CostStoragePersistenceTest} runs the real statements rather than mocking them.</p>
 */
public enum SqlDialect {

    H2 {
        @Override
        public String createChunkCostsTable() {
            return """
                CREATE TABLE IF NOT EXISTS chunk_costs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    world_name VARCHAR(255) NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    biome VARCHAR(255) NOT NULL,
                    difficulty VARCHAR(50) NOT NULL,
                    score INTEGER NOT NULL,
                    cost_type VARCHAR(50) NOT NULL,
                    vault_cost DOUBLE,
                    material_type VARCHAR(255),
                    material_amount INTEGER,
                    ai_processed BOOLEAN NOT NULL,
                    ai_explanation VARCHAR(1000),
                    calculated_at BIGINT NOT NULL,
                    config_hash VARCHAR(255) NOT NULL,
                    CONSTRAINT unique_chunk_cost UNIQUE(world_name, chunk_x, chunk_z, player_id, config_hash)
                )
                """;
        }

        @Override
        public String createChunkProfilesTable() {
            return """
                CREATE TABLE IF NOT EXISTS chunk_profiles (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    world_name VARCHAR(255) NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    material VARCHAR(255) NOT NULL,
                    block_count INTEGER NOT NULL,
                    tier INTEGER NOT NULL,
                    scanned_at BIGINT NOT NULL,
                    CONSTRAINT unique_chunk_profile UNIQUE(world_name, chunk_x, chunk_z, material)
                )
                """;
        }

        @Override
        public String createBaselineTable() {
            return """
                CREATE TABLE IF NOT EXISTS chunk_material_baseline (
                    material VARCHAR(255) NOT NULL PRIMARY KEY,
                    total_count BIGINT NOT NULL,
                    chunks_counted BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """;
        }

        @Override
        public String[] createIndexes() {
            return new String[] {
                "CREATE INDEX IF NOT EXISTS idx_chunk_location ON chunk_costs(world_name, chunk_x, chunk_z)",
                "CREATE INDEX IF NOT EXISTS idx_player_costs ON chunk_costs(player_id)",
                "CREATE INDEX IF NOT EXISTS idx_calculated_at ON chunk_costs(calculated_at)",
                "CREATE INDEX IF NOT EXISTS idx_profile_location ON chunk_profiles(world_name, chunk_x, chunk_z)"
            };
        }

        @Override
        public String upsertChunkCost() {
            // The KEY clause is load-bearing (#90): without it H2 matches on the PRIMARY KEY,
            // the auto-increment id this statement never supplies, and every write fails with
            // 90081 leaving chunk_costs empty on every server.
            return """
                MERGE INTO chunk_costs
                (world_name, chunk_x, chunk_z, player_id, biome, difficulty, score, cost_type,
                 vault_cost, material_type, material_amount, ai_processed, ai_explanation,
                 calculated_at, config_hash)
                KEY (world_name, chunk_x, chunk_z, player_id, config_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        }

        @Override
        public String upsertChunkProfile() {
            return """
                MERGE INTO chunk_profiles
                (world_name, chunk_x, chunk_z, material, block_count, tier, scanned_at)
                KEY (world_name, chunk_x, chunk_z, material)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        }

        @Override
        public String upsertBaseline() {
            // Parameters: material, material, blockCount, material, chunksProfiled, now.
            //
            // The chunksProfiled parameter in place of a literal 1 is load-bearing: a material
            // appearing for the first time inherits the number of chunks already profiled,
            // because those earlier chunks are genuine evidence that they contained none of
            // it. Averaging over only the chunks seen since it first appeared collapses
            // relative scoring back to raw abundance - the bug #86 exists to fix.
            return """
                MERGE INTO chunk_material_baseline (material, total_count, chunks_counted, updated_at)
                KEY (material)
                VALUES (?,
                        COALESCE((SELECT total_count FROM chunk_material_baseline WHERE material = ?), 0) + ?,
                        COALESCE((SELECT chunks_counted FROM chunk_material_baseline WHERE material = ?), ?),
                        ?)
                """;
        }

        @Override
        public void bindBaselineUpsert(PreparedStatement stmt, String material,
                                       int blockCount, long chunksProfiled, long now)
                throws SQLException {
            // The material repeats because each correlated subquery needs its own parameter.
            stmt.setString(1, material);
            stmt.setString(2, material);
            stmt.setInt(3, blockCount);
            stmt.setString(4, material);
            stmt.setLong(5, chunksProfiled);
            stmt.setLong(6, now);
        }

        @Override
        public String countProfiledChunks() {
            return "SELECT COUNT(DISTINCT (world_name || ':' || chunk_x || ',' || chunk_z)) AS profiled "
                + "FROM chunk_profiles";
        }
    },

    MYSQL {
        @Override
        public String createChunkCostsTable() {
            // Indexes are declared inline: MySQL has no CREATE INDEX IF NOT EXISTS, so a
            // separate statement would throw on every restart after the first.
            //
            // VARCHAR lengths are capped below H2's because the unique key spans five columns
            // and InnoDB limits an index to 3072 bytes; world_name at 255 utf8mb4 characters
            // would blow that on its own. 128 matches chunk_data in MySqlChunkDatabase.
            return "CREATE TABLE IF NOT EXISTS chunk_costs ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "world_name VARCHAR(128) NOT NULL,"
                + "chunk_x INT NOT NULL,"
                + "chunk_z INT NOT NULL,"
                + "player_id CHAR(36) NOT NULL,"
                + "biome VARCHAR(128) NOT NULL,"
                + "difficulty VARCHAR(50) NOT NULL,"
                + "score INT NOT NULL,"
                + "cost_type VARCHAR(50) NOT NULL,"
                + "vault_cost DOUBLE NULL,"
                + "material_type VARCHAR(128) NULL,"
                + "material_amount INT NULL,"
                + "ai_processed BOOLEAN NOT NULL,"
                + "ai_explanation VARCHAR(1000) NULL,"
                + "calculated_at BIGINT NOT NULL,"
                + "config_hash VARCHAR(64) NOT NULL,"
                + "UNIQUE KEY unique_chunk_cost (world_name, chunk_x, chunk_z, player_id, config_hash),"
                + "INDEX idx_chunk_location (world_name, chunk_x, chunk_z),"
                + "INDEX idx_player_costs (player_id),"
                + "INDEX idx_calculated_at (calculated_at)"
                + ")";
        }

        @Override
        public String createChunkProfilesTable() {
            return "CREATE TABLE IF NOT EXISTS chunk_profiles ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "world_name VARCHAR(128) NOT NULL,"
                + "chunk_x INT NOT NULL,"
                + "chunk_z INT NOT NULL,"
                + "material VARCHAR(128) NOT NULL,"
                + "block_count INT NOT NULL,"
                + "tier INT NOT NULL,"
                + "scanned_at BIGINT NOT NULL,"
                + "UNIQUE KEY unique_chunk_profile (world_name, chunk_x, chunk_z, material),"
                + "INDEX idx_profile_location (world_name, chunk_x, chunk_z)"
                + ")";
        }

        @Override
        public String createBaselineTable() {
            return "CREATE TABLE IF NOT EXISTS chunk_material_baseline ("
                + "material VARCHAR(128) NOT NULL PRIMARY KEY,"
                + "total_count BIGINT NOT NULL,"
                + "chunks_counted BIGINT NOT NULL,"
                + "updated_at BIGINT NOT NULL"
                + ")";
        }

        @Override
        public String[] createIndexes() {
            // Declared inline in CREATE TABLE above - MySQL has no IF NOT EXISTS for indexes.
            return new String[0];
        }

        @Override
        public String upsertChunkCost() {
            // ON DUPLICATE KEY UPDATE matches on unique_chunk_cost, the same constraint H2's
            // KEY clause names. The id column is left out so it is never part of the match,
            // which is precisely the mistake #90 was.
            return "INSERT INTO chunk_costs "
                + "(world_name, chunk_x, chunk_z, player_id, biome, difficulty, score, cost_type,"
                + " vault_cost, material_type, material_amount, ai_processed, ai_explanation,"
                + " calculated_at, config_hash) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "biome=VALUES(biome), difficulty=VALUES(difficulty), score=VALUES(score), "
                + "cost_type=VALUES(cost_type), vault_cost=VALUES(vault_cost), "
                + "material_type=VALUES(material_type), material_amount=VALUES(material_amount), "
                + "ai_processed=VALUES(ai_processed), ai_explanation=VALUES(ai_explanation), "
                + "calculated_at=VALUES(calculated_at)";
        }

        @Override
        public String upsertChunkProfile() {
            return "INSERT INTO chunk_profiles "
                + "(world_name, chunk_x, chunk_z, material, block_count, tier, scanned_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "block_count=VALUES(block_count), tier=VALUES(tier), scanned_at=VALUES(scanned_at)";
        }

        @Override
        public String upsertBaseline() {
            // MySQL cannot read chunk_material_baseline in a subquery while inserting into it
            // ("You can't specify target table for update in FROM clause"), so accumulation
            // moves into the update clause: a new material takes the VALUES row as-is
            // (total = blockCount, chunks = chunksProfiled), and an existing one accumulates
            // total_count while chunks_counted is left alone - it was already incremented for
            // every tracked material by bumpAllBaselineChunkCounts().
            return "INSERT INTO chunk_material_baseline (material, total_count, chunks_counted, updated_at) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "total_count = total_count + VALUES(total_count), "
                + "updated_at = VALUES(updated_at)";
        }

        @Override
        public void bindBaselineUpsert(PreparedStatement stmt, String material,
                                       int blockCount, long chunksProfiled, long now)
                throws SQLException {
            stmt.setString(1, material);
            stmt.setInt(2, blockCount);
            stmt.setLong(3, chunksProfiled);
            stmt.setLong(4, now);
        }

        @Override
        public String countProfiledChunks() {
            // CONCAT, not ||. In MySQL's default mode || is logical OR, so this would compare
            // three numbers-as-booleans, evaluate to 0 for every row, and report exactly one
            // distinct chunk however many were profiled. Every material's baseline would then
            // divide by 1 and read as its raw total - silently defeating #86's scoring rather
            // than throwing an error.
            return "SELECT COUNT(DISTINCT CONCAT(world_name, ':', chunk_x, ',', chunk_z)) AS profiled "
                + "FROM chunk_profiles";
        }
    };

    public abstract String createChunkCostsTable();

    public abstract String createChunkProfilesTable();

    public abstract String createBaselineTable();

    /** Index statements to run after the tables; empty when they are declared inline. */
    public abstract String[] createIndexes();

    /** Upsert one committed price. 15 parameters, in table order. */
    public abstract String upsertChunkCost();

    /** Upsert one material row of a chunk profile. 7 parameters, in table order. */
    public abstract String upsertChunkProfile();

    /**
     * Accumulate one material into the baseline. The parameter list differs between engines,
     * so bind it with {@link #bindBaselineUpsert} rather than by hand.
     */
    public abstract String upsertBaseline();

    /**
     * Bind {@link #upsertBaseline()}, which is the one statement whose shape differs enough
     * that the two engines take different parameters.
     *
     * <p>H2 accumulates by reading the row it is writing, so the material repeats once per
     * correlated subquery; MySQL cannot do that and accumulates in its update clause instead,
     * taking each value once. Padding MySQL out to H2's six parameters was tried and made the
     * statement unreadable to preserve a symmetry that does not exist - so the binding varies
     * with the SQL, right next to it.</p>
     *
     * @param chunksProfiled chunks profiled before this one. Load-bearing for a material's
     *        first appearance: it inherits this count rather than starting at 1, because
     *        those earlier chunks are real evidence that they held none of it (#86)
     */
    public abstract void bindBaselineUpsert(PreparedStatement stmt, String material,
                                            int blockCount, long chunksProfiled, long now)
            throws SQLException;

    /** Count distinct profiled chunks, aliased {@code profiled}. */
    public abstract String countProfiledChunks();

    /** Bump the chunk count for every tracked material. Identical in both engines. */
    public String bumpAllBaselineChunkCounts() {
        return "UPDATE chunk_material_baseline SET chunks_counted = chunks_counted + 1, updated_at = ?";
    }
}

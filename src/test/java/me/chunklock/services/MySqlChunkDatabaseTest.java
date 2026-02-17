package me.chunklock.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MySqlChunkDatabaseTest {

    @Test
    void shouldParseValidChunkKey() {
        MySqlChunkDatabase.ChunkKeyParts parts = MySqlChunkDatabase.parseChunkKey("world:12:-3");
        assertNotNull(parts);
        assertEquals("world", parts.worldName);
        assertEquals(12, parts.x);
        assertEquals(-3, parts.z);
    }

    @Test
    void shouldRejectInvalidChunkKeyFormat() {
        assertNull(MySqlChunkDatabase.parseChunkKey("world:12"));
        assertNull(MySqlChunkDatabase.parseChunkKey("world:abc:5"));
        assertNull(MySqlChunkDatabase.parseChunkKey(""));
    }
}

package me.chunklock.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageFactoryTest {

    @Test
    void shouldChooseMapDbWhenConfiguredTypeIsMapDb() {
        assertEquals(StorageFactory.StartupDecision.MAPDB,
                StorageFactory.resolveStartupDecision("mapdb", false, true));
    }

    @Test
    void shouldChooseMySqlWhenConfiguredAndInitialized() {
        assertEquals(StorageFactory.StartupDecision.MYSQL,
                StorageFactory.resolveStartupDecision("mysql", true, true));
    }

    @Test
    void shouldFailStartupWhenMySqlConfiguredAndFailFastEnabled() {
        assertEquals(StorageFactory.StartupDecision.FAILURE,
                StorageFactory.resolveStartupDecision("mysql", false, true));
    }

    @Test
    void shouldFallbackToMapDbWhenMySqlConfiguredAndFailFastDisabled() {
        assertEquals(StorageFactory.StartupDecision.MAPDB,
                StorageFactory.resolveStartupDecision("mysql", false, false));
    }

    // ---- Pricing storage routing (#95) --------------------------------------------------

    /**
     * #95 in one assertion: pricing data must follow {@code database.type} like every other
     * store. It used to open an H2 file unconditionally, so a MySQL network gave each node its
     * own committed prices (#83) and its own material baseline (#86) - a player saw a
     * different price for the same chunk depending which server they joined.
     */
    @Test
    void shouldRouteCostStorageToMySqlWhenMySqlModeIsActive() {
        assertEquals(StorageFactory.CostStorageDecision.MYSQL,
                StorageFactory.resolveCostStorage(true, true));
    }

    /**
     * Also the {@code fail-fast: false} fallback case: a MySQL server whose database is
     * unreachable falls back to MapDB, and pricing must fall back with it rather than pointing
     * at a pool that never initialized.
     */
    @Test
    void shouldRouteCostStorageToH2WhenMapDbIsSelected() {
        assertEquals(StorageFactory.CostStorageDecision.H2,
                StorageFactory.resolveCostStorage(false, false));
    }

    @Test
    void shouldNotRouteCostStorageToMySqlWithoutAConnectionProvider() {
        assertEquals(StorageFactory.CostStorageDecision.H2,
                StorageFactory.resolveCostStorage(true, false),
                "mysql mode without a provider is not a usable MySQL backend");
    }
}

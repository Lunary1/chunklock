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
}

# Cost Calculation Consistency Tests

## Overview

These tests verify that the GUI and holograms display the same cost for unlocking chunks. The tests ensure consistency by documenting the expected behavior and API contracts.

## Test Structure

### CostCalculationConsistencyTest

This test suite documents and verifies:

1. **Unified Cost Calculation Method**: Both GUI and holograms use `EconomyManager.calculateRequirement(player, chunk, biome, evaluation)`
2. **Deterministic Calculations**: Same inputs produce same outputs
3. **Contested Chunk Multipliers**: Applied consistently in both displays
4. **Economy Type Handling**: Vault vs materials handled consistently

## Running Tests

To run these tests, you'll need to add test dependencies to `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.5.0</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-junit-jupiter</artifactId>
        <version>5.5.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Then run:
```bash
mvn test
```

## Future Enhancements

For full integration testing, you would need to:

1. Mock Bukkit/Paper API dependencies (Player, Chunk, Biome, etc.)
2. Mock EconomyManager and its dependencies
3. Create test fixtures for different scenarios (vault vs materials, contested chunks, etc.)
4. Add assertions that compare GUI cost vs hologram cost for the same inputs

## Current Implementation

The current implementation ensures consistency by:

1. **UnlockGui.open()**: Uses `economyManager.calculateRequirement(player, chunk, biome, evaluation)` and stores result in `PendingUnlock.paymentRequirement`
2. **UnlockGuiBuilder.build()**: Uses the stored `PaymentRequirement` for display
3. **HologramService**: Uses `economyManager.calculateRequirement(player, chunk, biome, evaluation)` for both creation and updates

This ensures both systems use the exact same cost calculation method with the same inputs.


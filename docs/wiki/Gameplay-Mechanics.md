# Gameplay Mechanics

This guide explains how Chunklock's core gameplay systems work, providing players and server administrators with a deep understanding of the progression mechanics.

## Core Concept

Chunklock transforms Minecraft survival into a strategic progression game where players must methodically unlock chunks to expand their territory. Instead of freely exploring the world, players start in a limited area and gradually earn access to new regions through resource investment and strategic planning.

## Chunk Progression System

### Starting Area

Every player begins with a **single starting chunk** that serves as their initial safe zone:

**Starting Chunk Assignment**:

- Players are assigned one chunk (16x16 blocks) when they first join
- The system finds a suitable chunk with appropriate difficulty
- Chunk is automatically unlocked and owned by the player
- Location is chosen based on biome suitability and terrain

**Starting Chunk Features**:

- Always unlocked and accessible to the owner
- Cannot be lost or taken by other players
- Provides basic resources for early game progression
- Serves as respawn point and home base
- Forms the foundation for territorial expansion

### Unlocking Mechanics

Players expand their territory by unlocking adjacent chunks through resource investment:

#### Adjacency Requirement

```
✓ UNLOCKED    → ? LOCKED     → ? LOCKED
✓ UNLOCKED    → ✓ UNLOCKABLE → ? LOCKED
✓ STARTING    → ✓ UNLOCKED   → ? LOCKED
```

- **Starting chunk** (✓): Player's initial 16x16 block area
- **Unlocked chunks** (✓): Accessible areas owned by the player
- **Unlockable chunks** (→): Adjacent to owned chunks, can be purchased
- **Locked chunks** (?): Not adjacent, cannot be unlocked yet

#### Progression Flow

1. **Assessment**: Player explores edge of unlocked territory
2. **Target Selection**: Choose which adjacent chunk to unlock next
3. **Cost Calculation**: System calculates unlock cost based on multiple factors
4. **Resource Payment**: Player pays required materials or currency
5. **Unlock Confirmation**: Chunk becomes accessible, new adjacent chunks become unlockable

### Cost Calculation System

Chunk unlock costs are dynamically calculated using multiple factors to create balanced progression:

#### Base Cost Formula

```
Total Cost = Base Cost × Difficulty × Distance × Biome × Progress × Team
```

#### Factor Breakdown

**1. Base Cost**: Foundation cost from configuration

```yaml
economy:
  vault:
    base-cost: 100.0
  materials:
    base-requirements:
      STONE: 32
      COAL: 16
      IRON_INGOT: 8
```

**2. Difficulty Multiplier**: Progressive scaling

```yaml
economy:
  difficulty:
    distance-multiplier: 1.1 # 10% increase per chunk from spawn
    progress-multiplier: 1.05 # 5% increase per chunk unlocked
```

**3. Distance Factor**: Chunks further from spawn cost more

```
Distance 1: ×1.1 (110% of base cost)
Distance 5: ×1.61 (161% of base cost)
Distance 10: ×2.59 (259% of base cost)
```

**4. Biome Multiplier**: Different biomes have different costs

```yaml
economy:
  biome-multipliers:
    OCEAN: 0.8 # Cheaper - limited resources
    DESERT: 0.9 # Slightly cheaper
    PLAINS: 1.0 # Standard cost
    FOREST: 1.1 # Slightly expensive - good resources
    JUNGLE: 1.3 # Expensive - rich resources
    MOUNTAIN: 1.2 # Expensive - mining potential
    NETHER: 2.0 # Very expensive - dangerous but valuable
```

**5. Progress Scaling**: More unlocked chunks = higher costs

```
Chunks 1-10: Standard scaling
Chunks 11-25: +25% additional scaling
Chunks 26-50: +50% additional scaling
Chunks 51+: +75% additional scaling
```

**6. Team Bonus**: Teams get cost reductions

```yaml
teams:
  cost-reduction: 0.15 # 15% cost reduction for team members
```

#### Example Cost Calculation

**Scenario**: Player wants to unlock a Forest chunk that is:

- 5 chunks from spawn
- Their 15th unlocked chunk
- They're in a 4-person team

```
Base Cost: 100 coins
Distance: ×1.61 (5 chunks away)
Progress: ×1.75 (15th chunk)
Biome: ×1.1 (Forest)
Team: ×0.85 (15% discount)

Final Cost: 100 × 1.61 × 1.75 × 1.1 × 0.85 = 264 coins
```

## Economy Systems

Chunklock supports two distinct economy models to suit different server styles:

### Vault Economy

**Best for**: Servers with existing economy plugins

```yaml
economy:
  type: "vault"
  vault:
    base-cost: 100.0
    currency-name: "coins"
```

**Features**:

- Integrates with existing economy plugins (EssentialsX, CMI, etc.)
- Uses server's established currency
- Supports all standard economy commands
- Compatible with shops, trading, and other economic systems

**Player Experience**:

```
/balance              # Check current money
/chunklock cost       # Preview unlock cost
/chunklock unlock     # Purchase chunk with money
```

### Materials Economy

**Best for**: Pure survival servers, resource-focused gameplay

```yaml
economy:
  type: "materials"
  materials:
    base-requirements:
      STONE: 32
      COAL: 16
      IRON_INGOT: 8
      GOLD_INGOT: 2
```

**Features**:

- Requires specific items from player inventory
- Scales material costs with progression
- Encourages mining and resource gathering
- No external dependencies needed

**Player Experience**:

```
/chunklock cost       # Shows required materials
/chunklock unlock     # Consumes items from inventory
```

#### Material Scaling

Material requirements scale with the same multipliers as Vault economy:

```
Base Requirements:
- Stone: 32 blocks
- Coal: 16 pieces
- Iron Ingot: 8 pieces

At Distance 5, 15th chunk, Forest biome, with team:
- Stone: 32 × 2.61 = 84 blocks
- Coal: 16 × 2.61 = 42 pieces
- Iron Ingot: 8 × 2.61 = 21 pieces
```

## Team System

Teams fundamentally change the Chunklock experience from individual progression to collaborative expansion:

### Team Formation

**Creating Teams**:

```
/chunklock team create <name>    # Become team leader
/chunklock team invite <player>  # Invite players
/chunklock team join <team>      # Accept invitation
```

**Team Roles**:

- **Leader**: Full team management, can disband team
- **Moderator**: Can invite/kick members, manage permissions
- **Member**: Can unlock chunks, access team territory

### Shared Territory

Teams share all unlocked chunks among members:

```
Team "Builders" Territory:
Player A unlocks chunks → Accessible to all team members
Player B unlocks chunks → Accessible to all team members
Combined territory → Larger exploration area
```

**Benefits**:

- Faster territory expansion through shared effort
- Resource pooling for expensive chunks
- Collaborative base building across multiple chunks
- Shared strategic planning

### Team Economics

Teams receive significant economic advantages:

**Cost Reduction**:

```yaml
teams:
  cost-reduction: 0.15 # 15% discount on all unlocks
```

**Resource Pooling**: Team members can contribute materials/money collectively

**Strategic Unlocking**: Teams can plan expansion routes together

### Team Progression Example

```
Solo Player:
- Chunk 1-10: Standard costs
- Limited by individual resources
- Slower expansion rate

4-Person Team:
- 15% cost reduction on all chunks
- 4× resource generation potential
- Coordinated expansion strategies
- Shared territory benefits
```

## Progression Strategies

### Early Game (Chunks 1-10)

**Goals**:

- Establish resource production
- Plan expansion route
- Form or join a team

**Strategy**:

```
Priority 1: Secure resource-rich chunks (Forest, Mountain)
Priority 2: Plan route to specific biomes
Priority 3: Avoid expensive chunks unless necessary
```

**Recommended Route**:

1. Start with cheapest adjacent chunks
2. Target chunks with valuable resources
3. Create sustainable resource loops
4. Save expensive biomes for later

### Mid Game (Chunks 11-25)

**Goals**:

- Establish multiple resource bases
- Reach diverse biomes
- Optimize unlock efficiency

**Strategy**:

```
Priority 1: Connect to different biomes
Priority 2: Create resource networks
Priority 3: Plan for late-game expensive chunks
```

**Advanced Techniques**:

- **Biome Bridging**: Create efficient paths to valuable biomes
- **Resource Stockpiling**: Save for expensive late-game unlocks
- **Strategic Delay**: Sometimes worth waiting to unlock expensive chunks

### Late Game (Chunks 26+)

**Goals**:

- Access all desired biomes
- Maximize territory efficiency
- Complete megaprojects

**Strategy**:

```
Priority 1: Selective high-value unlocks
Priority 2: Optimize existing territory
Priority 3: Support team expansion
```

**Considerations**:

- Unlock costs become very expensive
- Focus on quality over quantity
- Team coordination becomes crucial

## Biome-Specific Strategies

### Resource-Rich Biomes

**Jungle** (×1.3 multiplier):

- **Pros**: Cocoa, unique wood, rich vegetation
- **Strategy**: Unlock for renewable resource farms
- **Timing**: Mid-game when you can afford the premium

**Mountain** (×1.2 multiplier):

- **Pros**: Exposed ores, stone variety, height advantage
- **Strategy**: Early priority for mining operations
- **Timing**: Early game for resource generation

### Specialized Biomes

**Ocean** (×0.8 multiplier):

- **Pros**: Cheap unlock, fishing resources, monument access
- **Strategy**: Use as cheap expansion routes
- **Timing**: When you need to connect distant areas

**Desert** (×0.9 multiplier):

- **Pros**: Sand, cacti, temples, relatively cheap
- **Strategy**: Moderate priority for specific resources
- **Timing**: Mid-game for specific projects

### High-Value Biomes

**Nether** (×2.0 multiplier):

- **Pros**: Unique resources, rapid travel, exclusive materials
- **Strategy**: Late-game team investment
- **Timing**: Only when team can pool resources

## Visual Feedback Systems

### Chunk Borders

Visual indicators help players understand territory boundaries:

```yaml
borders:
  enabled: true
  particle-type: "REDSTONE"
  height: 5
  density: 10
```

**Border Types**:

- **Red Particles**: Locked chunk boundaries
- **Green Particles**: Unlocked chunk boundaries
- **Yellow Particles**: Unlockable chunk boundaries
- **Blue Particles**: Team territory boundaries

### Holograms

Information displays provide real-time feedback:

```yaml
holograms:
  enabled: true
  show-unlock-cost: true
  show-chunk-info: true
  update-interval: 10
```

**Hologram Information**:

- Chunk coordinates
- Unlock cost
- Biome type
- Team ownership
- Unlock status

### User Interface

GUI systems make progression accessible:

```
/chunklock gui    # Open chunk management interface
```

**GUI Features**:

- Visual chunk map
- Cost calculations
- Unlock previews
- Team management
- Progress tracking

## Performance Considerations

Chunklock is designed to handle progression systems efficiently:

### Chunk Loading

```yaml
performance:
  chunk-loading:
    async: true # Non-blocking operations
    threads: 4 # Parallel processing
    max-operations-per-tick: 5 # Rate limiting
```

### Data Management

```yaml
performance:
  caching:
    player-data-cache: 500 # In-memory player data
    chunk-data-cache: 2000 # Chunk state caching
    cache-expiry: 1800 # 30-minute expiry
```

### Update Optimization

```yaml
performance:
  border-update-interval: 30 # Border refresh rate
  hologram-update-interval: 10 # Info display updates
  batch-process-size: 20 # Database batch size
```

## Integration with Vanilla Mechanics

Chunklock enhances rather than replaces Minecraft's core systems:

### Exploration

- **Modified**: Gated behind progression system
- **Enhanced**: Strategic route planning becomes important
- **Preserved**: All exploration mechanics remain once unlocked

### Building

- **Unrestricted**: Full building capabilities in unlocked chunks
- **Enhanced**: Territory expansion creates building goals
- **Strategic**: Chunk boundaries influence megaproject planning

### Resource Gathering

- **Gated**: Access to resources controlled by chunk unlocks
- **Enhanced**: Resource scarcity creates strategic decisions
- **Balanced**: Early game provides sufficient resources to progress

### Multiplayer Dynamics

- **Enhanced**: Team system creates collaborative goals
- **Strategic**: Resource sharing and territory planning
- **Competitive**: Teams can race for valuable territories

---

_Understanding these mechanics helps players develop effective strategies for territorial expansion and resource management in Chunklock._

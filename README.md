# Chunklock

**Version**: 2.2.0  
**Minecraft**: Paper 1.20.4+ (tested up to 1.21.10)  
**License**: Proprietary / Private

---

## 🔥 About Chunklock

Chunklock transforms Minecraft into a strategic progression-based survival game. Players start with a single unlocked chunk and must strategically expand their territory by unlocking adjacent chunks through resource investment. Each chunk unlock cost is dynamically calculated based on distance, biome difficulty, player progress, and team bonuses.

---

## 🧠 Core Mechanics

- **Strategic chunk-by-chunk progression** - Start with one chunk, unlock adjacent areas
- **Costs derived from the chunk being unlocked** - a forest costs wood, a mountain andesite, ranked by what makes each chunk distinctive rather than by what it holds most of
- **Dynamic cost calculation** - Prices scale with distance, biome type, and progress
- **Dual economy system** - Materials-based or Vault currency support
- **Team collaboration** - Share territory and costs with team members
- **Visual feedback** - Hologram displays, particle borders, and interactive GUIs
- **Biome-based difficulty** - Different biomes have unique unlock costs and challenges

---

## 🛠 Configuration

The plugin uses a comprehensive `config.yml` with the following main sections:

- **Economy Settings** - Materials or Vault-based economy with biome multipliers
- **Chunk Values** - Biome difficulty scoring and thresholds
- **Team System** - Team creation, management, and cost sharing
- **Visual Effects** - Holograms, borders, and UI customization
- **Performance** - Caching, threading, and optimization settings
- **Worlds** - Dedicated world configuration and player claims

### Storage backend (`database.yml`)

Core gameplay data (`chunks` + `players`) now supports two backends:

- `mapdb` (default): local embedded files (`chunks.db`, `players.db`)
- `mysql` (optional): external MySQL server with connection pooling

```yaml
database:
  type: "mapdb" # or "mysql"
  fail-fast: true
  mysql:
    host: "localhost"
    port: 3306
    database: "chunklock"
    username: "chunklock_user"
    password: "change_me"
    use-ssl: false
```

When switching from MapDB to MySQL, Chunklock performs a one-time automatic migration and writes `.mysql_migration_completed` in the plugin data folder after verification.

**→ For detailed MySQL setup instructions, see [MySQL Setup Guide](docs/user-guides/MySQL-Setup-Guide.md)**

### Upgrading from older versions

When updating from v1.1 or earlier, the plugin automatically migrates legacy YAML files into the new unified configuration format. Original files are preserved with a `.old` extension for safety.

---

## 🚀 Development Status

- ✅ **Core chunk system** - Complete unlock/lock mechanics with adjacency rules
- ✅ **Dynamic economy** - Materials and Vault integration with scaling costs
- ✅ **Team system** - Full collaboration features with shared territory
- ✅ **Visual effects** - Holograms, particle borders, and interactive GUIs
- ✅ **Admin commands** - Complete management and debugging tools
- ✅ **Service architecture** - Phase 2 dependency injection system implemented
- ✅ **Performance optimization** - Async processing and caching systems

---

## 🔧 Commands

### Player Commands

| Command                        | Description                                  |
| ------------------------------ | -------------------------------------------- |
| `/chunklock status`            | View your progress and current location      |
| `/chunklock start`             | Begin your chunk progression journey         |
| `/chunklock spawn`             | Return to your starting chunk                |
| `/chunklock team <subcommand>` | Team management (create, invite, join, etc.) |
| `/chunklock help`              | Show available commands                      |

### Admin Commands

| Command                      | Description                                   |
| ---------------------------- | --------------------------------------------- |
| `/chunklock setup <size>`    | Initialize a new Chunklock world              |
| `/chunklock reset <player>`  | Reset a player's progress                     |
| `/chunklock resetall`        | Reset all player data (requires confirmation) |
| `/chunklock bypass [player]` | Toggle movement restriction bypass            |
| `/chunklock reload`          | Reload plugin configuration                   |
| `/chunklock database`        | View storage backend status and MySQL details |
| `/chunklock debug`           | View system diagnostics and performance       |

---

## 🔗 Dependencies

### Required

- **Paper/Spigot/Pufferfish** 1.20.4+
- **Java** 17+

### Optional

- **Vault** - For currency-based economy (with any economy plugin)
- **[FancyHolograms](https://modrinth.com/plugin/fancyholograms)** - **Required for holograms.**
  Without it, chunk borders still work but unlock costs are not displayed above them. There is
  no built-in hologram fallback. Chunklock logs a warning at startup if holograms are enabled
  in `holograms.yml` but the provider cannot load.

  > **Java version note**: install a FancyHolograms build matching your server's Java version.
  > A jar built for a newer Java than the server runs is skipped silently at startup, so the
  > plugin appears simply absent. FancyHolograms publishes parallel `-java21` builds for this.

---

## 📂 Build Instructions

1. **Clone the repository**

   ```bash
   git clone https://github.com/Lunary1/chunklock.git
   cd chunklock
   ```

2. **Build with Maven**

   ```bash
   mvn clean package -DskipTests
   ```

3. **Install the plugin**
   ```bash
   # Copy from target/Chunklock-1.2.7.jar to your server's plugins folder
   cp target/Chunklock-*.jar /path/to/server/plugins/
   ```

---

## 📖 Documentation

Complete documentation is available in the GitHub Wiki:

- **[Installation Guide](Installation-Guide.md)** - Setup and configuration
- **[Player Commands](Player-Commands.md)** - Complete command reference
- **[Admin Commands](Admin-Commands.md)** - Administrative tools
- **[Configuration Reference](Configuration-Reference.md)** - Full config.yml documentation
- **[Gameplay Mechanics](Gameplay-Mechanics.md)** - How the progression system works
- **[API Documentation](API-Documentation.md)** - Developer integration guide
- **[Troubleshooting](Troubleshooting.md)** - Common issues and solutions
- **[Performance Optimization](Performance-Optimization.md)** - Server tuning guide

---

## 🎮 Quick Start

1. **Install** the plugin on your Paper 1.20.4+ server
2. **Setup** a dedicated world: `/chunklock setup myworld 30000`
3. **Configure** economy type in `config.yml` (materials or vault)
4. **Start playing** - players use `/chunklock start` to begin progression
5. **Unlock chunks** adjacent to owned territory with `/chunklock unlock`

---

## 💡 Features Highlight

- **Single chunk starting area** - Players begin with just one 16x16 block area
- **Strategic expansion** - Only adjacent chunks can be unlocked, requiring planning
- **Cost scaling** - Prices increase based on distance, biome, and total chunks owned
- **Team benefits** - 15% cost reduction and shared territory for team members
- **Visual guides** - Real-time cost displays, chunk borders, and unlock previews
- **Performance optimized** - Handles 100+ concurrent players with minimal TPS impact

---

> **Support**: For issues, feature requests, or questions, visit our [GitHub repository](https://github.com/Lunary1/chunklock) or contact the **LunaryCraft Dev Team**.

# Chunklock

**Version**: 1.0.0  
**Minecraft**: Paper 1.21.4+  
**License**: Proprietary / Private

---

## 🔥 About Chunklock

Chunklock transforms Minecraft into a strategic progression-based survival game. Players start in a single unlocked chunk and must expand their world by unlocking new chunks — each calculated by a configurable resource-based scoring system.

---

## 🧠 Core Mechanics

- **Resource-based unlocks**
- **Biome and distance scoring system**
- **Difficulty tiers (Easy → Impossible)**
- **Fully configurable scoring system via `config.yml`**
- **Supports both solo and team play**

---

## 🛠 Configuration

### 1️⃣ `config.yml`

- Combine chunk values, biome unlock costs and team settings
- Customize block/biome weights and difficulty thresholds
- Define items required to unlock biomes

---

### Upgrading from older versions

When updating from v1.1 or earlier, the plugin will automatically migrate
legacy YAML files (`chunk_values.yml`, `biome_costs.yml`, `team_config.yml`,
`player_chunks.yml`, `player_progress.yml`, `chunk_data.yml`, and
`teams_enhanced.yml`) into the new `config.yml`, `data.yml` and `teams.yml`
on first startup. Original files are preserved with a `.old` extension.

---

## 🚀 Development Status

- ✅ Core functionality complete
- ✅ Dynamic scoring system complete
- ✅ Team system planned
- ✅ Visual effects system planned (holograms, borders)
- ✅ Admin commands & QoL systems planned
- 🔄 Multiverse API integrated for personal world creation

---

## 🔧 Commands (WIP)

| Command           | Description             |
| ----------------- | ----------------------- |
| `/chunk unlock`   | Unlock facing chunk     |
| `/chunk info`     | Get unlock cost & biome |
| `/chunk home`     | Return to start         |
| `/chunk progress` | View your unlock stats  |
| `/chunk team ...` | Team system             |

---

## 🔗 Dependencies

- Paper 1.21.4+

---

## 📂 Build Instructions

1️⃣ Clone repository  
2️⃣ Run `mvn clean package`  
3️⃣ Copy JAR into your server's `/plugins` folder

---

> For development help, feature requests or collaboration, contact **LunaryCraft Dev Team**.

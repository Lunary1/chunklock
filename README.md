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
- **Fully configurable scoring system via `chunk_values.yml`**
- **Supports both solo and team play**

---

## 🛠 Configuration

### 1️⃣ `chunk_values.yml`

- Customize block and biome weights
- Set score thresholds for difficulty

### 2️⃣ `biome_costs.yml`

- Define which items are required to unlock chunks for each biome

---

## 🚀 Development Status

- ✅ Core functionality complete
- ✅ Dynamic scoring system complete
- ✅ Multiverse API integrated for personal world creation
- 🔄 Team system planned
- 🔄 Visual effects system planned (holograms, borders)
- 🔄 Admin commands & QoL systems planned

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

# Chunklock Documentation Index

Welcome to the Chunklock documentation! This folder contains all project documentation organized by audience and purpose.

---

## 📚 User Guides

Practical guides for server administrators and players setting up or using Chunklock.

- **[MySQL Setup Guide](user-guides/MySQL-Setup-Guide.md)** - Complete guide for migrating to MySQL storage backend
- **[Biome Unlocks Guide](user-guides/Biome-Unlocks-Guide.md)** - Comprehensive reference for configuring biome unlock costs (vanilla + custom items)
- **[Oraxen Integration Guide](user-guides/Oraxen-Integration-Guide.md)** - Setup, testing, and examples for Oraxen custom items
- **[Integration Testing Guide](user-guides/INTEGRATION-TESTING-GUIDE.md)** - Testing procedures for MMOItems and other custom item plugins
- **[Quick Start Deploy Guide](user-guides/QUICK-START-DEPLOY.md)** - Fast deployment instructions for production
- **[Dependency Testing](user-guides/dependency-testing.md)** - Testing Vault and FancyHolograms integrations

---

## 🔧 Developer References

Technical documentation for developers working on the Chunklock codebase.

- **[CODEX Context](developer/CODEX-CONTEXT.md)** - Fast context map for AI agents and developers
- **[AI Cost System](developer/ai-cost-system.md)** - OpenAI-powered dynamic pricing system design
- **[Modular Config System](developer/MODULAR-CONFIG-SYSTEM.md)** - Configuration architecture documentation
- **[Refactoring Plan](developer/refactoring-plan.md)** - Service-oriented architecture migration strategy
- **[Development Instructions](developer/instructions.md)** - Developer setup and build procedures

---

## 📖 Wiki (User-Facing)

The `wiki/` subfolder contains documentation for GitHub wiki or external hosted wiki:

- **[Home](wiki/Home.md)** - Wiki homepage
- **[Installation Guide](wiki/Installation-Guide.md)** - Setup instructions
- **[Player Commands](wiki/Player-Commands.md)** - Player command reference
- **[Admin Commands](wiki/Admin-Commands.md)** - Administrative command reference
- **[Configuration Reference](wiki/Configuration-Reference.md)** - Complete config.yml documentation
- **[Gameplay Mechanics](wiki/Gameplay-Mechanics.md)** - How progression works
- **[API Documentation](wiki/API-Documentation.md)** - Developer integration guide
- **[Troubleshooting](wiki/Troubleshooting.md)** - Common issues and solutions
- **[Performance Optimization](wiki/Performance-Optimization.md)** - Server tuning guide

---

## 📋 Project Meta

Strategic project documentation and marketing materials.

- **[Project Charter](project-charter.md)** - Premium product vision, strategy, and goals
- **[Roadmap](roadmap.md)** - Development roadmap and version history
- **[SpigotMC Description](SpigotMC-Description.md)** - Marketing copy for SpigotMC plugin listing

---

## 🗃️ Archive

Historical completion notes and implementation summaries preserved for reference.

See [archive/README.md](archive/README.md) for the full list of archived documents.

---

## 🔍 Finding What You Need

### If you want to...

- **Set up MySQL storage** → [MySQL Setup Guide](user-guides/MySQL-Setup-Guide.md)
- **Configure biome unlock costs** → [Biome Unlocks Guide](user-guides/Biome-Unlocks-Guide.md)
- **Integrate Oraxen custom items** → [Oraxen Integration Guide](user-guides/Oraxen-Integration-Guide.md)
- **Deploy to production** → [Quick Start Deploy](user-guides/QUICK-START-DEPLOY.md)
- **Understand the codebase** → [CODEX Context](developer/CODEX-CONTEXT.md)
- **Work with the AI cost system** → [AI Cost System](developer/ai-cost-system.md)
- **See configuration options** → [Wiki Configuration Reference](wiki/Configuration-Reference.md)
- **Troubleshoot issues** → [Wiki Troubleshooting](wiki/Troubleshooting.md)

---

## 📝 Documentation Standards

All documentation in this folder follows these standards:

- **Markdown format** - All docs use .md files
- **Clear headings** - Use H2 (##) for main sections, H3 (###) for subsections
- **Code examples** - YAML examples use ```yaml blocks with proper indentation
- **Emoji indicators** - 📚 User guides, 🔧 Developer docs, 📖 Wiki, 📋 Meta, 🗃️ Archive
- **Relative links** - Use relative paths for inter-document links
- **Status indicators** - Mark documents as [ACTIVE], [ARCHIVED], or [DEPRECATED]

---

## 🤝 Contributing

When adding new documentation:

1. **Choose the right folder**:
   - User-facing guides → `user-guides/`
   - Developer references → `developer/`
   - Wiki content → `wiki/`
   - Historical summaries → `archive/`

2. **Update this index** - Add your document to the appropriate section above

3. **Follow standards** - Use consistent formatting and structure

4. **Cross-link** - Link to related documents where appropriate

---

**Last Updated:** February 17, 2026  
**Documentation Version:** 2.1.0

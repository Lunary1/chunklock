# MySQL Storage Backend Setup Guide

This guide provides comprehensive instructions for setting up and using the MySQL storage backend for Chunklock.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [MySQL Server Setup](#mysql-server-setup)
- [Chunklock Configuration](#chunklock-configuration)
- [Migration from MapDB](#migration-from-mapdb)
- [Performance Tuning](#performance-tuning)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)
- [Best Practices](#best-practices)

---

## Overview

Chunklock supports two storage backends:

- **MapDB** (default): Embedded file-based database (`chunks.db`, `players.db`)
- **MySQL**: External relational database with connection pooling

### When to Use MySQL

Consider MySQL storage when you have:

- **Multiple servers** sharing the same Chunklock world
- **Large player base** (1000+ players)
- **High concurrent usage** (50+ players online simultaneously)
- **Existing MySQL infrastructure**
- **Need for external data access** (web dashboards, analytics)
- **Backup and replication requirements**

### Benefits of MySQL

- ✅ **Better scalability** for large player counts
- ✅ **Connection pooling** reduces overhead
- ✅ **External data access** for web integration
- ✅ **Replication and backup** built into MySQL
- ✅ **Concurrent access** from multiple servers
- ✅ **Data integrity** through ACID transactions

---

## Prerequisites

### MySQL Server Requirements

- MySQL 8.0+ or MariaDB 10.5+
- At least 512 MB RAM allocated to MySQL
- SSD storage recommended for best performance
- Network access between Minecraft server and MySQL server

### Minecraft Server Requirements

- Chunklock 2.1.0+
- Paper 1.21.10+
- Java 17+

---

## MySQL Server Setup

### Step 1: Install MySQL

**On Ubuntu/Debian:**

```bash
sudo apt update
sudo apt install mysql-server
sudo mysql_secure_installation
```

**On CentOS/RHEL:**

```bash
sudo yum install mysql-server
sudo systemctl start mysqld
sudo mysql_secure_installation
```

**On Windows:**
Download and install [MySQL Community Server](https://dev.mysql.com/downloads/mysql/)

### Step 2: Create Database and User

Connect to MySQL as root:

```bash
mysql -u root -p
```

Create database and user:

```sql
-- Create database
CREATE DATABASE chunklock CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user (replace 'your_password' with a strong password)
CREATE USER 'chunklock_user'@'localhost' IDENTIFIED BY 'your_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON chunklock.* TO 'chunklock_user'@'localhost';
FLUSH PRIVILEGES;

-- Verify
SHOW GRANTS FOR 'chunklock_user'@'localhost';
```

**For remote connections** (if MySQL is on a different server):

```sql
-- Create user with remote access (replace 'minecraft_server_ip' with actual IP)
CREATE USER 'chunklock_user'@'minecraft_server_ip' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON chunklock.* TO 'chunklock_user'@'minecraft_server_ip';
FLUSH PRIVILEGES;
```

### Step 3: Configure MySQL for Remote Access (if needed)

Edit MySQL configuration:

```bash
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf
```

Change `bind-address`:

```ini
# Before
bind-address = 127.0.0.1

# After (allow connections from any IP)
bind-address = 0.0.0.0
```

Restart MySQL:

```bash
sudo systemctl restart mysql
```

**Configure firewall** (if applicable):

```bash
# Ubuntu/Debian
sudo ufw allow 3306/tcp

# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=3306/tcp
sudo firewall-cmd --reload
```

### Step 4: Test Connection

From your Minecraft server:

```bash
mysql -h mysql_server_ip -u chunklock_user -p
```

---

## Chunklock Configuration

### Step 1: Edit database.yml

Navigate to `plugins/Chunklock/` and edit `database.yml`:

```yaml
database:
  type: "mysql" # Change from "mapdb" to "mysql"
  fail-fast: true # Stop startup if MySQL connection fails

  mysql:
    host: "localhost" # Or your MySQL server IP
    port: 3306
    database: "chunklock"
    username: "chunklock_user"
    password: "your_password" # Use the password from Step 2
    use-ssl: false # Set to true if using SSL certificates

    pool:
      max-size: 10 # Maximum connections in pool
      min-idle: 2 # Minimum idle connections
      connection-timeout-ms: 30000 # 30 seconds
      idle-timeout-ms: 600000 # 10 minutes
      max-lifetime-ms: 1800000 # 30 minutes

    cache:
      ttl-ms: 300000 # In-memory cache duration (5 minutes)
```

### Step 2: Save and Restart

Save the file and **restart** your Minecraft server (or run `/chunklock reload`).

### Step 3: Verify Connection

Run the following command in-game or console:

```
/chunklock database
```

You should see:

```
=== Chunklock Database Status ===

Storage Backend: MYSQL

MySQL Configuration:
  Host: localhost:3306
  Database: chunklock
  Username: chunklock_user
  SSL: Disabled

✓ Connection Status: Active

Connection Pool:
  Active Connections: 1
  Idle Connections: 2
  Total Connections: 3
  ...
```

---

## Migration from MapDB

### Automatic Migration

When you switch from MapDB to MySQL, Chunklock **automatically migrates** your existing data:

1. **First startup with MySQL config**: Plugin detects existing MapDB files
2. **Migration process**: All chunk and player data is copied to MySQL
3. **Verification**: Data integrity is checked
4. **Marker file**: `.mysql_migration_completed` is created
5. **Cleanup**: Original MapDB files are preserved (`.db` files remain as backups)

### Migration Steps

1. **Backup your data** (recommended):

   ```bash
   cp -r plugins/Chunklock plugins/Chunklock-backup
   ```

2. **Configure MySQL** in `database.yml` (see above)

3. **Restart server** - migration happens automatically

4. **Check logs** for migration status:

   ```
   [INFO] ✅ MySQL connection pool initialized
   [INFO] 📦 Migrating legacy YAML data into MapDB before MySQL import...
   [INFO] ✅ Data migration completed successfully
   [INFO] 🔄 Migrating 1234 chunks from MapDB to MySQL...
   [INFO] 🔄 Migrating 567 player records from MapDB to MySQL...
   [INFO] ✅ MySQL migration completed successfully
   ```

5. **Verify with command**:
   ```
   /chunklock database
   ```
   Look for: `Migration: MapDB to MySQL: Completed`

### Manual Migration (if automatic fails)

If automatic migration fails, you can manually export data:

1. **Set `fail-fast: false`** in `database.yml` to continue with MapDB
2. **Check logs** for error messages
3. **Fix issues** (connection problems, permissions, etc.)
4. **Set `fail-fast: true`** and restart to retry migration

---

## Performance Tuning

### Connection Pool Sizing

Adjust based on your server load:

**Small server (< 50 players):**

```yaml
pool:
  max-size: 10
  min-idle: 2
```

**Medium server (50-100 players):**

```yaml
pool:
  max-size: 20
  min-idle: 5
```

**Large server (100+ players):**

```yaml
pool:
  max-size: 30
  min-idle: 10
```

### Cache TTL

Balance between freshness and performance:

```yaml
cache:
  ttl-ms: 300000 # 5 minutes (default)
  # Lower for more up-to-date data, higher for better performance
```

### MySQL Server Optimization

Edit MySQL configuration (`/etc/mysql/mysql.conf.d/mysqld.cnf`):

```ini
# Buffer pool (set to 70% of available RAM for dedicated MySQL server)
innodb_buffer_pool_size = 2G

# Log file size
innodb_log_file_size = 256M

# Query cache (if MySQL < 8.0)
query_cache_type = 1
query_cache_size = 128M

# Max connections
max_connections = 200

# Table open cache
table_open_cache = 4000
```

Restart MySQL after changes:

```bash
sudo systemctl restart mysql
```

---

## Monitoring

### In-Game Monitoring

Use the database command to check status:

```
/chunklock database
```

Key metrics to watch:

- **Active Connections**: Should be low when server is idle
- **Idle Connections**: Should match or exceed `min-idle`
- **Threads Awaiting Connection**: Should be 0 (indicates pool exhaustion if > 0)
- **Query Response Time**: Should be < 50ms

### MySQL Monitoring

**Check active connections:**

```sql
SHOW PROCESSLIST;
```

**Check database size:**

```sql
SELECT table_schema AS "Database",
       ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS "Size (MB)"
FROM information_schema.TABLES
WHERE table_schema = "chunklock"
GROUP BY table_schema;
```

**Check table statistics:**

```sql
USE chunklock;
SHOW TABLE STATUS;
```

---

## Troubleshooting

### Connection Failures

**Symptoms**: Plugin fails to start, error message: `❌ Failed to initialize MySQL connection pool`

**Diagnostics:**

1. Test connection from Minecraft server:

   ```bash
   mysql -h your_host -P 3306 -u chunklock_user -p
   ```

2. Check MySQL logs:

   ```bash
   sudo tail -f /var/log/mysql/error.log
   ```

3. Verify user permissions:
   ```sql
   SHOW GRANTS FOR 'chunklock_user'@'localhost';
   ```

**Common fixes:**

- ✅ Double-check username/password in `database.yml`
- ✅ Verify MySQL is running: `sudo systemctl status mysql`
- ✅ Check firewall rules (port 3306)
- ✅ Ensure user has correct host permissions (`@'localhost'` vs `@'%'`)

### Slow Queries

**Symptoms**: Server lag, slow chunk loading

**Diagnostics:**

1. Check query response time with `/chunklock database`
2. Enable MySQL slow query log:

   ```sql
   SET GLOBAL slow_query_log = 'ON';
   SET GLOBAL long_query_time = 1;  -- Log queries > 1 second
   ```

3. Review slow queries:
   ```bash
   sudo tail -f /var/log/mysql/mysql-slow.log
   ```

**Fixes:**

- ✅ Increase `innodb_buffer_pool_size` in MySQL config
- ✅ Reduce `cache.ttl-ms` to reduce database hits
- ✅ Upgrade to SSD storage
- ✅ Optimize MySQL tables: `OPTIMIZE TABLE chunk_data, player_data;`

### Connection Pool Exhaustion

**Symptoms**: Error: `Threads Awaiting Connection: 5+`

**Fixes:**

- ✅ Increase `pool.max-size` in `database.yml`
- ✅ Decrease `pool.connection-timeout-ms` to fail faster
- ✅ Check for connection leaks in plugin logs

### Data Loss or Corruption

**Symptoms**: Chunks unlocked incorrectly, player progress lost

**Recovery:**

1. **Restore from MapDB backup:**

   ```bash
   # Stop server
   cp plugins/Chunklock-backup/*.db plugins/Chunklock/
   # Change database.yml to type: "mapdb"
   # Start server
   ```

2. **Restore from MySQL backup:**
   ```bash
   mysql -u root -p chunklock < chunklock_backup.sql
   ```

---

## Best Practices

### Security

- ✅ Use **strong passwords** for MySQL users
- ✅ Create **dedicated MySQL user** with minimal permissions
- ✅ Enable **SSL** for remote connections
- ✅ **Firewall** MySQL port (3306) to only Minecraft server IP
- ✅ Never commit `database.yml` with passwords to version control

### Backups

**Automated daily backup:**

```bash
#!/bin/bash
# backup-chunklock-db.sh
mysqldump -u chunklock_user -p'your_password' chunklock > /backups/chunklock-$(date +%Y%m%d).sql

# Keep last 7 days
find /backups -name "chunklock-*.sql" -mtime +7 -delete
```

Schedule with cron:

```bash
crontab -e
# Add: 0 3 * * * /path/to/backup-chunklock-db.sh
```

### Monitoring

Set up alerts for:

- MySQL server downtime
- Connection pool exhaustion
- Slow query thresholds
- Disk space usage

### Scaling

For **very large servers** (500+ players):

- Consider MySQL replication (master-slave setup)
- Use dedicated MySQL server (separate machine)
- Monitor and tune MySQL regularly
- Consider read replicas for analytics/dashboards

---

## Support

If you encounter issues not covered in this guide:

1. Check plugin logs: `plugins/Chunklock/logs/`
2. Check MySQL logs: `/var/log/mysql/error.log`
3. Run diagnostic command: `/chunklock database`
4. Join support Discord or file a GitHub issue

---

## Summary Checklist

- [ ] MySQL server installed and secured
- [ ] Database and user created with permissions
- [ ] `database.yml` configured with correct credentials
- [ ] Connection tested with `/chunklock database`
- [ ] Migration completed successfully
- [ ] Backups configured
- [ ] Performance tuned for your server size
- [ ] Monitoring set up

**Congratulations!** Your Chunklock plugin is now running on MySQL storage.

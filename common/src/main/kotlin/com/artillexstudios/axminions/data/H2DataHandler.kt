package com.artillexstudios.axminions.data

import com.artillexstudios.axapi.serializers.Serializers
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.data.DataHandler
import com.artillexstudios.axminions.api.minions.Direction
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import org.h2.jdbc.JdbcConnection
import java.sql.Statement
import java.sql.Types
import java.util.Properties
import java.util.UUID

class H2DataHandler : DataHandler {
    private lateinit var dataSource: HikariDataSource

    override fun getType(): String {
        return "H2"
    }

    override fun setup() {
        val config = HikariConfig()
        config.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource")
        config.addDataSourceProperty("url", "jdbc:h2:async:./${AxMinionsPlugin.INSTANCE.dataFolder}/data")
        config.setAutoCommit(true)
        dataSource = HikariDataSource(config) 

        dataSource.connection.use { connection ->
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS `axminions_types`(`id` INT AUTO_INCREMENT PRIMARY KEY, `name` VARCHAR(64));")
                .use {
                    it.executeUpdate()
                }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS `axminions_users`(`uuid` UUID PRIMARY KEY, `name` VARCHAR(16), `island_slots` INT);")
                .use {
                    it.executeUpdate()
                }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS `axminions_worlds`(`id` INT AUTO_INCREMENT PRIMARY KEY, `name` VARCHAR(64));")
                .use {
                    it.executeUpdate()
                }
        }


        dataSource.connection.use { connection ->
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS `axminions_locations`(`id` INT AUTO_INCREMENT PRIMARY KEY, `x` INT, `y` INT, `z` INT, `world_id` INT, FOREIGN KEY(world_id) REFERENCES `axminions_worlds`(`id`));")
                .use {
                    it.executeUpdate()
                }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS `axminions_minions`(`id` INT AUTO_INCREMENT PRIMARY KEY, `location_id` INT, `chest_location_id` INT, `owner_id` UUID, `type_id` TINYINT, `direction` TINYINT, `level` SMALLINT, `storage` DOUBLE, `actions` BIGINT, `tool` CLOB, FOREIGN KEY(`location_id`) REFERENCES `axminions_locations`(id), FOREIGN KEY(`chest_location_id`) REFERENCES `axminions_locations`(`id`), FOREIGN KEY(`owner_id`) REFERENCES `axminions_users`(`uuid`), FOREIGN KEY(`type_id`) REFERENCES `axminions_types`(`id`));")
                .use {
                    it.executeUpdate()
                }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("ALTER TABLE `axminions_minions` ADD COLUMN IF NOT EXISTS `charge` BIGINT DEFAULT(0);").use {
                it.executeUpdate()
            }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("ALTER TABLE `axminions_minions` ADD COLUMN IF NOT EXISTS `working` BOOLEAN DEFAULT(TRUE);").use {
                it.executeUpdate()
            }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("ALTER TABLE `axminions_minions` ADD COLUMN IF NOT EXISTS `extra_chests` CLOB;").use {
                it.executeUpdate()
            }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("ALTER TABLE `axminions_users` ADD COLUMN IF NOT EXISTS `island_slots` INT DEFAULT(0);").use {
                it.executeUpdate()
            }
        }

        if (Config.ISLAND_LIMIT() > 0) {
            dataSource.connection.use { connection ->
                connection.prepareStatement("CREATE TABLE IF NOT EXISTS `axminions_island_counter`(`island` VARCHAR(256) PRIMARY KEY, `placed` INT);")
                    .use {
                        it.executeUpdate()
                    }
            }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS `axminions_trusted`(`owner_id` UUID, `trusted_id` UUID, `permissions` VARCHAR(512), PRIMARY KEY(`owner_id`, `trusted_id`));")
                .use {
                    it.executeUpdate()
                }
        }

        // The players trusted before the permissions existed have none saved, they get the default ones
        dataSource.connection.use { connection ->
            connection.prepareStatement("ALTER TABLE `axminions_trusted` ADD COLUMN IF NOT EXISTS `permissions` VARCHAR(512);").use {
                it.executeUpdate()
            }
        }
    }

    override fun insertType(minionType: MinionType) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("MERGE INTO `axminions_types`(`name`) KEY(`name`) VALUES(?);").use {
                it.setString(1, minionType.getName())
                it.executeUpdate()
            }
        }
    }

    override fun loadMinionsForWorld(minionType: MinionType, world: World) {
        var typeId = 0
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT `id` FROM `axminions_types` WHERE `name` = ?;").use { statement ->
                statement.setString(1, minionType.getName())
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        typeId = resultSet.getInt("id")
                    }
                }
            }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT `minions`.* FROM `axminions_minions` AS `minions` JOIN `axminions_locations` AS `location` ON `minions`.`location_id` = `location`.`id` WHERE `location`.`world_id` = (SELECT `id` FROM `axminions_worlds` WHERE `name` = ?) AND `type_id` = ?;").use { statement ->
                statement.setString(1, world.name)
                statement.setInt(2, typeId)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val locationId = resultSet.getInt("location_id")
                        val chestLocationId = resultSet.getInt("chest_location_id")
                        val ownerId = resultSet.getObject("owner_id") as UUID
                        val direction = Direction.entries[resultSet.getByte("direction").toInt()]
                        val level = resultSet.getShort("level")
                        val storage = resultSet.getDouble("storage")
                        val actions = resultSet.getLong("actions")
                        val tool = resultSet.getString("tool")
                        val charge = resultSet.getLong("charge")
                        val workingValue = resultSet.getBoolean("working")
                        // Minions saved before the Start/Stop button existed have no value, they work
                        val working = resultSet.wasNull() || workingValue

                        val location = getLocation(locationId)
                        var chestLocation: Location? = null
                        if (chestLocationId != 0) {
                            chestLocation = getLocation(chestLocationId)
                        }
                        val extraChests = ChestLocations.deserialize(resultSet.getString("extra_chests"))

                        var itemStack = ItemStack(Material.AIR)
                        if (tool != null) {
                            itemStack = Serializers.ITEM_STACK.deserialize(tool)
                        }

                        com.artillexstudios.axminions.minions.Minion(
                            location!!,
                            ownerId,
                            Bukkit.getOfflinePlayer(ownerId),
                            minionType,
                            level.toInt(),
                            itemStack,
                            chestLocation,
                            direction,
                            actions,
                            storage,
                            locationId,
                            chestLocationId,
                            charge,
                            working,
                            extraChests
                        )
                    }
                }
            }
        }
    }

    override fun getLocationID(location: Location): Int {
        var worldId = 0
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "MERGE INTO `axminions_worlds`(`name`) KEY(`name`) VALUES(?);",
                Statement.RETURN_GENERATED_KEYS
            ).use { statement ->
                statement.setString(1, location.world?.name)
                statement.executeUpdate()

                statement.generatedKeys.use { resultSet ->
                    if (resultSet.next()) {
                        worldId = resultSet.getInt(1)
                    }
                }
            }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "MERGE INTO `axminions_locations`(`x`, `y`, `z`, `world_id`) KEY(`x`, `y`, `z`, `world_id`) VALUES (?, ?, ?, ?);",
                Statement.RETURN_GENERATED_KEYS
            ).use { statement ->
                statement.setInt(1, location.blockX)
                statement.setInt(2, location.blockY)
                statement.setInt(3, location.blockZ)
                statement.setInt(4, worldId)
                statement.executeUpdate()

                statement.generatedKeys.use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt(1)
                    }
                }
            }
        }

        return 0
    }

    override fun getLocation(locationId: Int): Location? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM `axminions_locations` WHERE `id` = ?;").use { statement ->
                statement.setInt(1, locationId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        val worldId = resultSet.getInt("world_id")
                        val x = resultSet.getInt("x")
                        val y = resultSet.getInt("y")
                        val z = resultSet.getInt("z")

                        return Location(getWorld(worldId), x.toDouble(), y.toDouble(), z.toDouble())
                    }

                    return null
                }
            }
        }
    }

    override fun getWorld(worldId: Int): World? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT `name` FROM `axminions_worlds` WHERE `id` = ?;").use { statement ->
                statement.setInt(1, worldId)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return Bukkit.getWorld(resultSet.getString("name"))
                    }

                    return null
                }
            }
        }
    }

    override fun saveMinion(minion: com.artillexstudios.axminions.api.minions.Minion) {
        val locationId = getLocationID(minion.getLocation())
        var linkedChestId: Int? = null
        var userId: UUID? = null
        var minionTypeId = 0

        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM `axminions_types` WHERE `name` = ?;").use {
                it.setString(1, minion.getType().getName())
                it.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        minionTypeId = resultSet.getInt("id")
                    }
                }
            }
        }

        if (minion.getLinkedChest() != null) {
            linkedChestId = getLocationID(minion.getLinkedChest()!!)
        }

        val extra = getExtraSlots(minion.getOwnerUUID())

        dataSource.connection.use { connection ->       
            connection.prepareStatement(
                "MERGE INTO `axminions_users`(`uuid`, `name`, `island_slots`) KEY(`uuid`) VALUES (?,?,?);",
                Statement.RETURN_GENERATED_KEYS
            ).use { statement ->
                statement.setObject(1, minion.getOwnerUUID())
                statement.setString(2, minion.getOwner()?.name ?: "---")
                statement.setInt(3, extra)
                statement.executeUpdate()

                statement.generatedKeys.use { resultSet ->
                    if (resultSet.next()) {
                        userId = resultSet.getObject(1) as UUID
                    }
                }
            }
        }

        if (userId == null) {
            return
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("MERGE INTO `axminions_minions`(`location_id`, `chest_location_id`, `owner_id`, `type_id`, `direction`, `level`, `storage`, `actions`, `tool`, `charge`, `working`, `extra_chests`) KEY(`location_id`) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")
                .use { statement ->
                    statement.setInt(1, locationId)
                    if (linkedChestId == null) {
                        statement.setNull(2, Types.INTEGER)
                    } else {
                        statement.setInt(2, linkedChestId)
                    }
                    statement.setObject(3, userId!!)
                    statement.setInt(4, minionTypeId)
                    statement.setByte(5, minion.getDirection().ordinal.toByte())
                    statement.setInt(6, minion.getLevel())
                    statement.setDouble(7, minion.getStorage())
                    statement.setLong(8, minion.getActionAmount())
                    if (minion.getTool() == null || minion.getTool()?.type == Material.AIR) {
                        statement.setNull(9, Types.CLOB)
                    } else {
                        statement.setString(9, Serializers.ITEM_STACK.serialize(minion.getTool()))
                    }
                    statement.setLong(10, minion.getCharge())
                    statement.setBoolean(11, minion.isWorking())
                    // The first container has its own column
                    val extraChests = ChestLocations.serialize(minion.getLinkedChests().drop(1))
                    if (extraChests == null) {
                        statement.setNull(12, Types.CLOB)
                    } else {
                        statement.setString(12, extraChests)
                    }
                    statement.executeUpdate()
                }
        }
    }

    override fun deleteMinion(minion: com.artillexstudios.axminions.api.minions.Minion) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM `axminions_minions` WHERE `location_id` = ?;")
                .use { preparedStatement ->
                    preparedStatement.setInt(1, minion.getLocationId())
                    preparedStatement.executeUpdate()
                }
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM `axminions_minions` WHERE `location_id` = ?;").use { statement ->
                statement.setInt(1, minion.getLocationId())
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        connection.prepareStatement("DELETE FROM `axminions_locations` WHERE `id` = ?").use {
                            it.setInt(1, minion.getLocationId())
                            it.executeUpdate()
                        }
                    }
                }
            }
        }

        if (minion.getChestLocationId() != 0) {
            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT * FROM `axminions_minions` WHERE `chest_location_id` = ?;")
                    .use { statement ->
                        statement.setInt(1, minion.getChestLocationId())
                        statement.executeQuery().use { resultSet ->
                            if (!resultSet.next()) {
                                connection.prepareStatement("DELETE FROM `axminions_locations` WHERE `id` = ?").use {
                                    it.setInt(1, minion.getLocationId())
                                    it.executeUpdate()
                                }
                            }
                        }
                    }
            }
        }
    }

    override fun getMinionAmount(uuid: UUID): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(`owner_id`) FROM `axminions_minions` WHERE `owner_id` = ?;")
                .use { statement ->
                    statement.setObject(1, uuid)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            return resultSet.getInt(1)
                        }
                    }
                }
        }

        return 0
    }

    override fun getMinionAmount(uuid: UUID, type: String): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM `axminions_minions` WHERE `owner_id` = ? AND `type_id` = (SELECT `id` FROM `axminions_types` WHERE `name` = ?);")
                .use { statement ->
                    statement.setObject(1, uuid)
                    statement.setString(2, type)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            return resultSet.getInt(1)
                        }
                    }
                }
        }

        return 0
    }

    override fun getMinionAmounts(uuid: UUID): Map<String, Int> {
        val amounts = HashMap<String, Int>()
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT `types`.`name`, COUNT(*) FROM `axminions_minions` AS `minions` JOIN `axminions_types` AS `types` ON `minions`.`type_id` = `types`.`id` WHERE `minions`.`owner_id` = ? GROUP BY `types`.`name`;")
                .use { statement ->
                    statement.setObject(1, uuid)
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            amounts[resultSet.getString(1)] = resultSet.getInt(2)
                        }
                    }
                }
        }

        return amounts
    }

    override fun isMinion(location: Location): Boolean {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT * FROM `axminions_minions` WHERE `location_id` = (SELECT `id` FROM `axminions_locations` WHERE x = ? AND y = ? AND z = ? AND `world_id` = (SELECT `id` FROM `axminions_worlds` WHERE `name` = ?));")
                .use { statement ->
                    statement.setInt(1, location.blockX)
                    statement.setInt(2, location.blockY)
                    statement.setInt(3, location.blockZ)
                    statement.setString(4, location.world?.name ?: "---")
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            return true
                        }
                    }
                }
        }

        return false
    }

    override fun islandPlace(island: String) {
        dataSource.connection.use { connection ->
            val placed = getIsland(island) + 1
            connection.prepareStatement("MERGE INTO `axminions_island_counter`(`placed`, `island`) KEY(`island`) VALUES(?,?);").use { statement ->
                statement.setInt(1, placed)
                statement.setString(2, island)
                statement.executeUpdate()
            }
        }
    }

    override fun islandReset(island: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE `axminions_island_counter` SET `placed` = 0 WHERE `island` = ?;").use { statement ->
                statement.setString(1, island)
                statement.executeUpdate()
            }
        }
    }

    override fun islandBreak(island: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE `axminions_island_counter` SET `placed` = `placed` - 1 WHERE `island` = ?;").use { statement ->
                statement.setString(1, island)
                statement.executeUpdate()
            }
        }
    }

    override fun getIsland(island: String): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT `placed` FROM `axminions_island_counter` WHERE `island` = ?;").use { statement ->
                statement.setString(1, island)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt("placed")
                    }
                }
            }
        }

        return 0
    }

    override fun addUser(uuid: UUID, name: String) {
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement("INSERT INTO `axminions_users`(`uuid`, `name`, `island_slots`) VALUES (?,?,?);").use { statement ->
                    statement.setObject(1, uuid)
                    statement.setString(2, name)
                    statement.setInt(3, 0)
                    statement.executeUpdate()
                }
            }
        } catch (exception: Exception) {

        }
    }

    override fun addExtraSlot(user: UUID, amount: Int) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE `axminions_users` SET `island_slots` = `island_slots` + ? WHERE `uuid` = ?;").use { statement ->
                statement.setInt(1, amount)
                statement.setObject(2, user)
                statement.executeUpdate()
            }
        }
    }

    override fun getExtraSlots(user: UUID): Int {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT `island_slots` FROM `axminions_users` WHERE `uuid` = ?;").use { statement ->
                statement.setObject(1, user)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        return resultSet.getInt("island_slots")
                    }

                    return 0
                }
            }
        }
    }

    override fun loadTrustedUsers(): Map<UUID, Map<UUID, String?>> {
        val trusted = HashMap<UUID, MutableMap<UUID, String?>>()

        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT `owner_id`, `trusted_id`, `permissions` FROM `axminions_trusted`;").use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        val owner = resultSet.getObject("owner_id") as UUID
                        val user = resultSet.getObject("trusted_id") as UUID
                        trusted.getOrPut(owner) { HashMap() }[user] = resultSet.getString("permissions")
                    }
                }
            }
        }

        return trusted
    }

    override fun addTrustedUser(owner: UUID, trusted: UUID, permissions: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("MERGE INTO `axminions_trusted`(`owner_id`, `trusted_id`, `permissions`) KEY(`owner_id`, `trusted_id`) VALUES(?, ?, ?);").use { statement ->
                statement.setObject(1, owner)
                statement.setObject(2, trusted)
                statement.setString(3, permissions)
                statement.executeUpdate()
            }
        }
    }

    override fun setTrustedPermissions(owner: UUID, trusted: UUID, permissions: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE `axminions_trusted` SET `permissions` = ? WHERE `owner_id` = ? AND `trusted_id` = ?;").use { statement ->
                statement.setString(1, permissions)
                statement.setObject(2, owner)
                statement.setObject(3, trusted)
                statement.executeUpdate()
            }
        }
    }

    override fun removeTrustedUser(owner: UUID, trusted: UUID) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM `axminions_trusted` WHERE `owner_id` = ? AND `trusted_id` = ?;").use { statement ->
                statement.setObject(1, owner)
                statement.setObject(2, trusted)
                statement.executeUpdate()
            }
        }
    }

    override fun disable() {
        dataSource.connection.prepareStatement("SHUTDOWN DEFRAG;").executeUpdate()
    }
}
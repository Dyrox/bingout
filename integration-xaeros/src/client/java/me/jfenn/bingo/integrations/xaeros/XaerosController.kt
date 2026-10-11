package me.jfenn.bingo.integrations.xaeros

import me.jfenn.bingo.client.common.packet.ClientPacketEvents
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.event.IEventBus
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import xaero.common.core.XaeroMinimapCore
import xaero.hud.minimap.world.MinimapWorld
import xaero.map.core.XaeroWorldMapCore
import xaero.map.file.MapSaveLoad
import java.io.IOException
import java.nio.file.Path
import java.util.function.Consumer


class XaerosController(
    private val log: Logger,
    environment: IModEnvironment,
    eventBus: IEventBus,
    events: ClientPacketEvents,
) {
    private fun resetXaerosMapData() {
        log.info("[XaerosController] Clearing map data...")
        val mapProcessor = XaeroWorldMapCore.currentSession.mapProcessor

        // Delete all map files
        deleteFolderFromDisk(MapSaveLoad.getRootFolder(mapProcessor.currentWorldId));
        // Force a reload from disk
        mapProcessor.mapWorld.currentDimension.clear();
    }

    private fun resetXaerosWaypointData() {
        log.info("[XaerosController] Clearing waypoint data...")
        val waypointSession = XaeroMinimapCore.currentSession.minimapProcessor.session.waypointSession
        val container = waypointSession.session.getWorldManager().currentRootContainer

        container.allWorldsIterable.forEach(Consumer { minimapWorld: MinimapWorld? ->
            minimapWorld!!.currentWaypointSet.clear()
            try {
                container.getSession().worldManagerIO.saveWorld(minimapWorld)
            } catch (_: IOException) {
                log.error("Failed to save map after deleting Xaeros waypoints! Waypoints will most likely stay behind!");
            }
        })
    }

    private fun deleteFolderFromDisk(path: Path) {
        if (FileUtils.isDirectory(path.toFile())) {
            try {
                log.info(String.format("Deleting Xaero's worldmap Folder at %s", path));
                FileUtils.deleteDirectory(path.toFile())
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    init {
        val minimapLoaded = environment.isModLoaded("xaerominimap")
        val worldmapLoaded = environment.isModLoaded("xaeroworldmap")

        log.info("[XaerosController] Initialized with minimap:${minimapLoaded}, worldmap:${worldmapLoaded}")
        if (minimapLoaded || worldmapLoaded) {
            eventBus.register(events.gameResetV1) {
                // If you don't have worldmap loaded, resetting the map is kind of pointless.
                // All the area you can view in the minimap gets reloaded all the time anyhow.
                if (worldmapLoaded) {
                    resetXaerosMapData()
                }

                // Minimap deals with Waypoints. If only worldmap is loaded, there are no waypoints to delete.
                if (minimapLoaded) {
                    resetXaerosWaypointData()
                }
            }
        }
    }
}
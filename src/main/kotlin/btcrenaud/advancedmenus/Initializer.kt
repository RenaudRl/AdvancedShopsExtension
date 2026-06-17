package btcrenaud.advancedmenus

import org.bukkit.plugin.java.JavaPlugin
import com.typewritermc.engine.paper.TypewriterPaperPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.typewritermc.engine.paper.extensions.placeholderapi.PlaceholderHandler
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import org.koin.dsl.bind
import btcrenaud.advancedmenus.services.*

@Singleton
object Initializer : Initializable, KoinComponent {

    private val advancedMenusModule = module {
        single { DelayedCommandService() }
        single { BungeeService() }
        single { ResourceService() }
        single { ScreenDetectionService() }
        single { ImageDownloadService() }
        single { CommandPlaceholderService() } bind PlaceholderHandler::class
        single {
            TextDisplayService()
        }
        single {
            val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
            MenuSessionService(plugin, get())
        }
    }

    override suspend fun initialize() {
        loadKoinModules(advancedMenusModule)

        // Start screen detection listener
        val screenDetectionService: ScreenDetectionService by inject()
        screenDetectionService.start()

        // Start menu session listener
        val sessionService: MenuSessionService by inject()
        sessionService.start()

        val plugin = JavaPlugin.getPlugin(TypewriterPaperPlugin::class.java)
        val commandService: CommandPlaceholderService by inject()

        // Register Command
        // Register Command dynamically via CommandMap to bypass plugin.yml requirement
        try {
            val commandMapField = plugin.server.javaClass.getDeclaredField("commandMap")
            commandMapField.isAccessible = true
            val commandMap = commandMapField.get(plugin.server) as org.bukkit.command.CommandMap
            
            val cmd = object : org.bukkit.command.defaults.BukkitCommand("advancedmenus") {
                init {
                    description = "AdvancedMenus management command"
                    usage = "/advancedmenus"
                    aliases = listOf("am")
                }
                override fun execute(sender: org.bukkit.command.CommandSender, commandLabel: String, args: Array<out String>): Boolean {
                    return commandService.onCommand(sender, this, commandLabel, args)
                }
                override fun tabComplete(sender: org.bukkit.command.CommandSender, alias: String, args: Array<out String>): List<String> {
                    return listOf("fov").filter { it.startsWith(args.last().lowercase()) }
                }
            }
            commandMap.register("advancedmenus", cmd)
        } catch (e: Exception) {
            plugin.getCommand("advancedmenus")?.setExecutor(commandService)
        }
    }

    override suspend fun shutdown() {
        // Gracefully shutdown services with packet listeners
        try {
            val sessionService: MenuSessionService by inject()
            sessionService.shutdown()
        } catch (_: Exception) {}

        try {
            val textDisplayService: TextDisplayService by inject()
            textDisplayService.clearAll()
        } catch (_: Exception) {}

        try {
            val screenService: ScreenDetectionService by inject()
            screenService.shutdown()
        } catch (_: Exception) {}

        unloadKoinModules(advancedMenusModule)
    }
}

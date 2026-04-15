package kurenai.imsyncbot.configuration

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import kurenai.imsyncbot.command.CommandDispatcher

@DependencyGraph(AppScope::class)
interface AppGraph {
    val commandDispatcher: CommandDispatcher
}

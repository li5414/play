package play.example.robot.module.player

import mu.KLogging
import org.springframework.stereotype.Component
import play.example.player.message.PlayerProto
import play.example.robot.module.CommandModule
import play.example.robot.module.PlayerModule

/**
 *
 * @author LiangZengle
 */
@Component
class PlayerModuleImpl(private val commandModule: CommandModule) : PlayerModule() {
  companion object : KLogging()

  override fun createResp(player: RobotPlayer, data: Boolean, req: CreateRequestParams?) {
    loginReq(player)
  }

  override fun loginResp(player: RobotPlayer, data: PlayerProto, req: Any?) {
    player.id = data.id
    player.name = data.name
    println("$player logged in")
    pingReq(player, "hello")
    commandModule.listReq(player)
  }

  override fun pingResp(player: RobotPlayer, data: String, req: PingRequestParams?) {
    logger.info("$player >> pong: ${req?.msg} $data")
    pingReq(player, "hello")
  }

  override fun StringMessageResp(player: RobotPlayer, data: String, req: Any?) {
    TODO("Not yet implemented")
  }
}

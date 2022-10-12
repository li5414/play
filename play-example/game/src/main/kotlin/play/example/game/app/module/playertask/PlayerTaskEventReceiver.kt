package play.example.game.app.module.playertask

import mu.KLogging
import org.springframework.stereotype.Component
import play.example.game.app.module.player.PlayerManager.Self
import play.example.game.app.module.player.event.PlayerEventBus
import play.example.game.app.module.player.event.subscribe
import play.example.game.app.module.playertask.event.AbstractPlayerTaskEvent
import play.example.game.app.module.task.entity.AbstractTask
import play.example.game.app.module.task.res.AbstractTaskResource

/**
 * 任务事件接收器
 *
 * @author LiangZengle
 */
@Component
class PlayerTaskEventReceiver(
  private val taskServices: List<AbstractPlayerTaskService<AbstractTask, AbstractTaskResource>>,
  eventBus: PlayerEventBus
) {

  companion object : KLogging()

  init {
    eventBus.subscribe(::onEvent)
  }

  private fun onEvent(self: Self, event: AbstractPlayerTaskEvent) {
    val taskService = this.taskServices
    for (i in taskService.indices) {
      val service = taskService[i]
      try {
        service.onEvent(self, event)
      } catch (e: Exception) {
        logger.error(e) { "任务事件处理失败: $self $event" }
      }
    }
  }
}

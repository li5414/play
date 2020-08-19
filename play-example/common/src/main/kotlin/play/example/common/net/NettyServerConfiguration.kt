package play.example.common.net

import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.WriteBufferWaterMark
import mu.KLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import play.Orders
import play.ShutdownCoordinator
import play.net.netty.NettyServerBuilder
import play.net.netty.createEventLoopGroup

/**
 *
 * @author LiangZengle
 */
@Configuration(proxyBeanMethods = false)
class NettyServerConfiguration {

  companion object : KLogging()

  @Bean("bossEventLoopGroup")
  fun bossEventLoopGroup(shutdownCoordinator: ShutdownCoordinator): EventLoopGroup {
    val executor = createEventLoopGroup("netty-boss", 1)
    shutdownCoordinator
      .addShutdownTask("Shutdown bossEventLoopGroup", Orders.Highest, executor) {
        it.shutdownGracefully().await()
      }
    return executor
  }

  @Bean("workerEventLoopGroup")
  fun workerEventLoopGroup(shutdownCoordinator: ShutdownCoordinator): EventLoopGroup {
    val executor = createEventLoopGroup("netty-worker", 0)
    shutdownCoordinator
      .addShutdownTask("Shutdown workerEventLoopGroup", Orders.lowerThan(Orders.Highest), executor) {
        it.shutdownGracefully().await()
      }
    return executor
  }

  @Bean
  fun nettyServerBuilder(
    @Qualifier("bossEventLoopGroup") parent: EventLoopGroup,
    @Qualifier("workerEventLoopGroup") child: EventLoopGroup,
  ): NettyServerBuilder {
    return NettyServerBuilder()
      .eventLoopGroup(parent, child)
      .option(ChannelOption.SO_REUSEADDR, true)
      .childOption(ChannelOption.TCP_NODELAY, true)
      .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark.DEFAULT)
      .childOption(ChannelOption.SO_RCVBUF, 8 * 1024)
      .childOption(ChannelOption.SO_SNDBUF, 32 * 1024)
  }
}
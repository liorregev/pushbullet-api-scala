package com.github.liorregev.pushbullet.listener

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import ch.qos.logback.classic.LoggerContext
import com.github.liorregev.pushbullet.domain.Push
trait EphemeralPushHandler { def onEphemeralPush(push: Push): Unit }

class NewPushProcessor(pushHandler: EphemeralPushHandler)
                      (implicit loggerFactory: LoggerContext)
  extends GraphStage[FlowShape[NewPush, NewPush]]{

  private val logger = loggerFactory.getLogger(this.getClass)
  val in: Inlet[NewPush] = Inlet("NopSink.In")
  val out: Outlet[NewPush] = Outlet("NopSink.Out")
  override val shape: FlowShape[NewPush, NewPush] = FlowShape.of(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val newPush = grab(in)
        logger.info(s"Got new ephemeral push")
        pushHandler.onEphemeralPush(newPush.push)
        push(out, newPush)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}

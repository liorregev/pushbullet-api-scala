package com.github.liorregev.pushbullet.listener

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import ch.qos.logback.classic.LoggerContext

class ReconnectProcessor(implicit loggerFactory: LoggerContext) extends GraphStage[FlowShape[Reconnect.type, Reconnect.type]]{
  private val logger = loggerFactory.getLogger(this.getClass)
  val in: Inlet[Reconnect.type] = Inlet("ReconnectSink.In")
  val out: Outlet[Reconnect.type] = Outlet("ReconnectSink.Out")
  override val shape: FlowShape[Reconnect.type, Reconnect.type] = FlowShape.of(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        logger.info("Got Reconnect")
        push(out, Reconnect)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}

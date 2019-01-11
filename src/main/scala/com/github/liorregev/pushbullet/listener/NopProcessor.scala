package com.github.liorregev.pushbullet.listener

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import ch.qos.logback.classic.LoggerContext

class NopProcessor(implicit loggerFactory: LoggerContext) extends GraphStage[FlowShape[Nop.type, Nop.type]]{
  private val logger = loggerFactory.getLogger(this.getClass)
  val in: Inlet[Nop.type] = Inlet("NopSink.In")
  val out: Outlet[Nop.type] = Outlet("NopSink.Out")
  override val shape: FlowShape[Nop.type, Nop.type] = FlowShape.of(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        logger.info("Got Nop")
        push(out, Nop)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}

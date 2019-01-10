package com.github.liorregev.pushbullet.listener

import akka.stream.{Attributes, Inlet, SinkShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import ch.qos.logback.classic.LoggerContext

class NopSink(implicit loggerFactory: LoggerContext) extends GraphStage[SinkShape[Nop.type]]{
  private val logger = loggerFactory.getLogger(this.getClass)
  val in: Inlet[Nop.type] = Inlet("NopSink")
  override val shape: SinkShape[Nop.type] = SinkShape(in)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        logger.debug("Got Nop")
      }
    })
  }
}

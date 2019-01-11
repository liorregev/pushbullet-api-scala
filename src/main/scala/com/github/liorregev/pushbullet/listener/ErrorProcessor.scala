package com.github.liorregev.pushbullet.listener

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import ch.qos.logback.classic.LoggerContext
trait ErrorHandler { def onError(error: ListenerError): Unit }

class ErrorProcessor(errorHandler: ErrorHandler)
                    (implicit loggerFactory: LoggerContext)
  extends GraphStage[FlowShape[ListenerError, ListenerError]]{

  private val logger = loggerFactory.getLogger(this.getClass)
  val in: Inlet[ListenerError] = Inlet("ErrorProcessor.In")
  val out: Outlet[ListenerError] = Outlet("ErrorProcessor.Out")
  override val shape: FlowShape[ListenerError, ListenerError] = FlowShape.of(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val error = grab(in)
        logger.warn(s"Got error $error")
        errorHandler.onError(error)
        push(out, error)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}

package com.github.liorregev.pushbullet.listener

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

class MapStage[A, B](f: A ⇒ B) extends GraphStage[FlowShape[A, B]] {
  val in: Inlet[A] = Inlet[A]("Map.in")
  val out: Outlet[B] = Outlet[B]("Map.out")
  override val shape: FlowShape[A, B] = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          push(out, f(grab(in)))
        }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
}
package com.github.liorregev.pushbullet.listener

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}

class SplitJunction extends GraphStage[FanOutShape4[Either[ListenerError, ProtoMessage], Nop.type, NewPush, Tickle, ListenerError]] {
  val in: Inlet[Either[ListenerError, ProtoMessage]] = Inlet[Either[ListenerError, ProtoMessage]]("Split.in")
  val nopOut: Outlet[Nop.type] = Outlet[Nop.type]("Split.NopOut")
  val pushOut: Outlet[NewPush] = Outlet[NewPush]("Split.NewPushOut")
  val tickleOut: Outlet[Tickle] = Outlet[Tickle]("Split.TickleOut")
  val errorOut: Outlet[ListenerError] = Outlet[ListenerError]("Split.ErrorOut")

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          grab(in) match {
            case Right(Nop) => push(nopOut, Nop)
            case Right(tickle: Tickle) => push(tickleOut, tickle)
            case Right(newPush: NewPush) => push(pushOut, newPush)
            case Left(error) => push(errorOut, error)
          }
        }
      })
    }
  override val shape: FanOutShape4[Either[ListenerError, ProtoMessage], Nop.type, NewPush, Tickle, ListenerError] =
    new FanOutShape4(in, nopOut, pushOut, tickleOut, errorOut)
}
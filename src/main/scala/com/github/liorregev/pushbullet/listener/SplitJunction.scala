package com.github.liorregev.pushbullet.listener

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.collection.mutable

class SplitJunction
  extends GraphStage[FanOutShape5[Either[ListenerError, ProtoMessage], Nop.type, NewPush, Tickle, ListenerError, Reconnect.type]] {

  val in: Inlet[Either[ListenerError, ProtoMessage]] = Inlet[Either[ListenerError, ProtoMessage]]("Split.in")
  val nopOut: Outlet[Nop.type] = Outlet("Split.NopOut")
  val pushOut: Outlet[NewPush] = Outlet("Split.NewPushOut")
  val tickleOut: Outlet[Tickle] = Outlet("Split.TickleOut")
  val errorOut: Outlet[ListenerError] = Outlet("Split.ErrorOut")
  val reconnectOut: Outlet[Reconnect.type] = Outlet("Split.ReconnectOut")

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      private val nopQueue = mutable.Queue[Nop.type]()
      private val pushQueue = mutable.Queue[NewPush]()
      private val tickleQueue = mutable.Queue[Tickle]()
      private val errorQueue = mutable.Queue[ListenerError]()
      private val reconnectQueue = mutable.Queue[Reconnect.type]()

      case class MessageOutHandler[T](outlet: Outlet[T], queue: mutable.Queue[T]) extends  OutHandler {
        override def onPull(): Unit = {
          if(queue.nonEmpty)
            push(outlet, queue.dequeue())
          else if(!hasBeenPulled(in))
            pull(in)
        }
      }

      setHandler(in, new InHandler {
        private def handleMessage[T](outlet: Outlet[T], queue: mutable.Queue[T])(message: T): Unit = {
          if(isAvailable(outlet))
            push(outlet, message)
          else
            queue.enqueue(message)
        }

        override def onPush(): Unit = {
          grab(in) match {
            case Right(Reconnect) => handleMessage(reconnectOut, reconnectQueue)(Reconnect)
            case Right(Nop) => handleMessage(nopOut, nopQueue)(Nop)
            case Right(tickle: Tickle) => handleMessage(tickleOut, tickleQueue)(tickle)
            case Right(newPush: NewPush) => handleMessage(pushOut, pushQueue)(newPush)
            case Right(_) =>
            case Left(error) => handleMessage(errorOut, errorQueue)(error)
          }
        }
      })
      setHandler(nopOut, MessageOutHandler(nopOut, nopQueue))
      setHandler(pushOut, MessageOutHandler(pushOut, pushQueue))
      setHandler(tickleOut, MessageOutHandler(tickleOut, tickleQueue))
      setHandler(errorOut, MessageOutHandler(errorOut, errorQueue))
      setHandler(reconnectOut, MessageOutHandler(reconnectOut, reconnectQueue))
    }
  override val shape: FanOutShape5[Either[ListenerError, ProtoMessage], Nop.type, NewPush, Tickle, ListenerError, Reconnect.type] =
    new FanOutShape5(in, nopOut, pushOut, tickleOut, errorOut, reconnectOut)
}
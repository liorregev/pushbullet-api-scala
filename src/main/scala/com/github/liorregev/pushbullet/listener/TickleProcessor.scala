package com.github.liorregev.pushbullet.listener

import java.time.Instant

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import ch.qos.logback.classic.LoggerContext
import com.github.liorregev.pushbullet
import com.github.liorregev.pushbullet.domain.{Device, DeviceListRequest, DeviceListResponse, Push, PushListRequest, PushListResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
trait PushHandler { def onPush(push: Push): Unit }
trait DeviceHandler { def onDevice(device: Device): Unit }
class TickleProcessor(client: pushbullet.Client, pushHandler: PushHandler, deviceHandler: DeviceHandler)
                     (implicit loggerFactory: LoggerContext, ec: ExecutionContext)
  extends GraphStage[FlowShape[Tickle, Tickle]]{

  private val logger = loggerFactory.getLogger(this.getClass)
  val in: Inlet[Tickle] = Inlet("NopSink.In")
  val out: Outlet[Tickle] = Outlet("NopSink.Out")
  override val shape: FlowShape[Tickle, Tickle] = FlowShape.of(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var lastFetchedPushes = Instant.ofEpochMilli(0)
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var lastFetchedDevices = Instant.ofEpochMilli(0)

    private def fetchInitialTimes(): Future[Unit] = client.request(PushListRequest())
      .flatMap {
        case Right(PushListResponse(pushes, _)) =>
          pushes.headOption.foreach(push => {
            lastFetchedPushes = push.baseInfo.modified
            lastFetchedDevices = push.baseInfo.modified
          })
          Future.successful(())
        case _ => fetchInitialTimes()
      }

    private val updateTimesFuture = fetchInitialTimes()

    private def processTickle(tickle: Tickle): Unit = tickle match {
      case Tickle(TickleSubtype.Push) =>
        val fetchingTs = Instant.ofEpochMilli(lastFetchedPushes.toEpochMilli)
        client.request(PushListRequest(modifiedAfter = Option(fetchingTs))).andThen {
          case Success(Right(PushListResponse(pushes, _))) =>
            pushes
              .filter(_.baseInfo.modified.isAfter(fetchingTs))
              .foreach(pushHandler.onPush)
          case error =>
            println(error)
        }
        lastFetchedPushes = Instant.now
      case Tickle(TickleSubtype.Device) =>
        val fetchingTs = Instant.ofEpochMilli(lastFetchedDevices.toEpochMilli)
        client.request(DeviceListRequest(modifiedAfter = Option(fetchingTs))).foreach {
          case Right(DeviceListResponse(devices, _)) =>
            devices
              .filter(_.baseInfo.modified.isAfter(fetchingTs))
              .foreach(deviceHandler.onDevice)
          case _ =>
        }
        lastFetchedDevices = Instant.now
    }

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val tickle = grab(in)
        logger.info(s"Got tickle $tickle")
        if(!updateTimesFuture.isCompleted)
          updateTimesFuture.foreach(_ => processTickle(tickle))
        else
          processTickle(tickle)
        push(out, tickle)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}

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
  val in: Inlet[Tickle] = Inlet("TickleProcessor.In")
  val out: Outlet[Tickle] = Outlet("TickleProcessor.Out")
  override val shape: FlowShape[Tickle, Tickle] = FlowShape.of(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var lastFetchedPushes = Instant.ofEpochMilli(0)
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var lastFetchedDevices = Instant.ofEpochMilli(0)

    private def fetchInitialTimes(): Future[Unit] = client.loadLatestServerTime()
      .map { time =>
        lastFetchedPushes = time
        lastFetchedDevices = time
      }

    private val updateTimesFuture = fetchInitialTimes()

    private def processTickle(tickle: Tickle): Unit = tickle match {
      case Tickle(TickleSubtype.Push) =>
        val fetchingTs = Instant.ofEpochMilli(lastFetchedPushes.toEpochMilli)
        client.request(PushListRequest(modifiedAfter = Option(fetchingTs))).andThen {
          case Success(Right(PushListResponse(pushes, _))) =>
            val filteredPushes = pushes.filter(_.baseInfo.modified.isAfter(fetchingTs))
            filteredPushes
              .map(_.baseInfo.modified)
              .reduceOption(implicitly[Ordering[Instant]].max)
              .foreach(lastFetchedPushes = _)
            filteredPushes
              .foreach(pushHandler.onPush)
          case error =>
            println(error)
        }
      case Tickle(TickleSubtype.Device) =>
        val fetchingTs = Instant.ofEpochMilli(lastFetchedDevices.toEpochMilli)
        client.request(DeviceListRequest(modifiedAfter = Option(fetchingTs))).foreach {
          case Right(DeviceListResponse(devices, _)) =>
            val filteredDevices = devices.filter(_.baseInfo.modified.isAfter(fetchingTs))
            filteredDevices
              .map(_.baseInfo.modified)
              .reduceOption(implicitly[Ordering[Instant]].max)
              .foreach(lastFetchedDevices = _)
            filteredDevices
              .foreach(deviceHandler.onDevice)
          case _ =>
        }
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

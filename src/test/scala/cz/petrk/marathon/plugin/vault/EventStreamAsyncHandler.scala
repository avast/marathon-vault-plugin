package cz.petrk.marathon.plugin.vault

import java.nio.charset.StandardCharsets
import java.util

import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient._

import scala.concurrent.{Future, Promise}
import collection.JavaConversions._

case class SSEvent(eventType: Option[String], eventData: String, eventId: Option[String])

class EventStreamAsyncHandler(onEvent: SSEvent => Boolean, onEventStreamEnd: () => Unit) extends AsyncHandler[SSEvent] {
  def this(onEvent: SSEvent => Boolean) = this(onEvent, () =>  Unit)

  val currentBuffer = new StringBuilder
  var result: SSEvent = _

  var eventType: Option[String] = None
  var eventId: Option[String] = None
  val eventDataBuffer = new StringBuilder

  override def onCompleted(): SSEvent = result

  override def onThrowable(t: Throwable): Unit = Unit

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): State = {
    val bodyPartString = new String(bodyPart.getBodyPartBytes, StandardCharsets.UTF_8)
    currentBuffer.append(bodyPartString)

    if (currentBuffer.isEmpty) {
      return State.CONTINUE
    }

    var remainingBufferStartIndex = 0
    var delimiterIndex = 0
    var atLeastOneDelimiterFound = false

    while({ delimiterIndex = currentBuffer.indexOf("\r\n", remainingBufferStartIndex); delimiterIndex } > -1) {
      atLeastOneDelimiterFound = true
      val state = onLine(currentBuffer.substring(remainingBufferStartIndex, Math.max(delimiterIndex, 0)))
      if (state == State.ABORT) { return state }
      remainingBufferStartIndex = delimiterIndex + 2
    }

    if (atLeastOneDelimiterFound) {
      currentBuffer.delete(0, remainingBufferStartIndex)
    }

    if (bodyPart.isLast) {
      if (currentBuffer.nonEmpty) {
        onLine(currentBuffer.toString())
      }
      onEventStreamEnd
      return State.ABORT
    }

    State.CONTINUE
  }

  private def onLine(line: String): State = {
    // processing as per https://www.w3.org/TR/eventsource/
    // except: "retry" field is not supported, BOM is not supported

    if (line.startsWith(":")) { return State.CONTINUE } // SSE comment

    if (line.length == 0) {
      // dispatch event
      val state = if (eventDataBuffer.nonEmpty) {
        val event = SSEvent(eventType, eventDataBuffer.toString(), eventId)
        if (onEvent(event)) State.ABORT else State.CONTINUE
      } else {
        State.CONTINUE
      }

      eventType = None
      eventDataBuffer.clear()

      return state
    }

    // parse field:value line

    val colonIndex = line.indexOf(":")
    val (field, value) = colonIndex match {
      case i if i != -1 =>
        (line.substring(0, colonIndex).trim, line.substring(colonIndex + 1).trim)
      case _ => (line, "")
    }

    field match {
      case "event" => eventType = Some(value)
      case "id" => eventId = Some(value)
      case "data" =>
        if (eventDataBuffer.nonEmpty) { eventDataBuffer.append("\n") }
        eventDataBuffer.append(value)
    }

    State.CONTINUE
  }

  override def onStatusReceived(responseStatus: HttpResponseStatus): State = {
    if (responseStatus.getStatusCode >= 200 && responseStatus.getStatusCode < 300) {
      State.CONTINUE
    } else {
      throw new RuntimeException(s"Non-success status returned, status code=${responseStatus.getStatusCode}, status text=${responseStatus.getStatusText}")
    }
  }

  override def onHeadersReceived(headers: HttpResponseHeaders): State = {
    State.CONTINUE
  }
}

class MarathonEventStream(marathonUrl: String) {
  private def onEventArrived(event: SSEvent): Boolean = {
    listeners.filter(l => l.predicate(event)).toList.foreach { l =>
      listeners.remove(l)
      l.promise.success(event)
    }
    false
  }

  private def onEventStreamEnd(): Unit = {
    listeners.foreach(l => l.promise.failure(new RuntimeException("The event stream was closed.")))
  }

  private val streamClient = new DefaultAsyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
    .setRequestTimeout(-1).build())
    .prepareGet(marathonUrl + "/v2/events")
    .addHeader("Accept", "text/event-stream")
    .execute(new EventStreamAsyncHandler(onEventArrived, onEventStreamEnd))

  private val listeners = new util.ArrayList[Listener]

  private case class Listener(predicate: SSEvent => Boolean, promise: Promise[SSEvent])

  def when(predicate: (SSEvent) => Boolean): Future[SSEvent] = {
    val promise = Promise[SSEvent]
    listeners.add(Listener(predicate, promise))
    promise.future
  }

  def close(): Unit = {
    streamClient.done()
  }
}

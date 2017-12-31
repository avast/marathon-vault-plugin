package com.avast.marathon.plugin.vault

import java.lang.reflect.Type
import java.time.{Duration, Instant}

import feign.codec.{Decoder, StringDecoder}
import feign.{Feign, Param, RequestLine, Response}
import feign.gson.GsonDecoder

import collection.JavaConversions._
import scala.beans.BeanInfo

object MesosAgentClient {
  private val contentTypeHeader = "Content-Type"
  private val gson = new GsonDecoder
  private val string = new StringDecoder

  def apply(agentUrl: String): MesosAgentClient = {
    Feign.builder()
      .decoder(new Decoder {
        override def decode(response: Response, `type`: Type): AnyRef = {
          if (response.headers().containsKey(contentTypeHeader)) {
            val value = response.headers().get(contentTypeHeader).head
            if (value.contains("json")) return gson.decode(response, `type`)
          }

          string.decode(response, `type`)
        }
      })
      .target(classOf[MesosAgentClient], agentUrl)
  }

  implicit class MesosAgentClientEx(agentClient: MesosAgentClient) {
    def waitForStdOutContentsMatch(envVarName: String, executor: MesosExecutor, fn: String => Option[String], timeout: Duration): String = {
      val stdOutPath = s"${executor.directory}/stdout"
      var matchOption: Option[String] = None
      var stdOut: String = null

      val maxTime = Instant.now().plus(timeout)

      do {
        if (Instant.now().compareTo(maxTime) > 1) {
          throw new RuntimeException("Timed out when waiting for task stdout to match.")
        }

        stdOut = agentClient.download(stdOutPath)
        matchOption = EnvAppCmd.extractEnvValue(envVarName, stdOut)
      } while (matchOption.isEmpty)

      matchOption.get
    }
  }
}

trait MesosAgentClient {
  @RequestLine("GET /state")
  def fetchState(): MesosAgentState

  @RequestLine("GET /files/download?path={path}")
  def download(@Param("path") path: String): String
}


@BeanInfo
case class MesosFramework(id: String, executors: Array[MesosExecutor])

@BeanInfo
case class MesosExecutor(id: String, name: String, directory: String)

@BeanInfo
case class MesosAgentState(frameworks: Array[MesosFramework])
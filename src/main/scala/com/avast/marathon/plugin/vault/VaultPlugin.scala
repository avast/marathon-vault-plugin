package com.avast.marathon.plugin.vault

import mesosphere.marathon.plugin.plugin.PluginConfiguration
import mesosphere.marathon.plugin.task.RunSpecTaskProcessor
import mesosphere.marathon.plugin.{ApplicationSpec, PodSpec}
import org.apache.mesos.Protos
import org.apache.mesos.Protos.ExecutorInfo.Builder
import org.apache.mesos.Protos.{TaskGroupInfo, TaskInfo}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, _}

case class Configuration(address: String, token: String)

class VaultPlugin extends RunSpecTaskProcessor with PluginConfiguration {

  private val logger = LoggerFactory.getLogger(classOf[VaultPlugin])
  logger.info("Vault plugin instantiated")

  private var configuration: Configuration = _

  override def initialize(marathonInfo: Map[String, Any], configurationJson: JsObject): Unit = {
    configuration = configurationJson.as[Configuration](Json.reads[Configuration])
    logger.info(s"VaultPlugin initialized with $configuration")
  }

  def taskInfo(appSpec: ApplicationSpec, builder: TaskInfo.Builder): Unit = {
    assert(configuration != null, "VaultPlugin not initalized with configuration info.")
    assert(configuration.address != null, "Vault address not specified.")
    assert(configuration.token != null, "Vault token not specified.")

    val envBuilder = builder.getCommand.getEnvironment.toBuilder

    envBuilder.addVariables(Protos.Environment.Variable.newBuilder()
      .setName("TESTVAR")
      .setValue("TESTVALUE"))

    val commandBuilder = builder.getCommand.toBuilder
    commandBuilder.setEnvironment(envBuilder)
    builder.setCommand(commandBuilder)
  }

  def taskGroup(podSpec: PodSpec, executor: Builder, taskGroup: TaskGroupInfo.Builder): Unit = {

  }
}

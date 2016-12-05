package cz.petrk.marathon.plugin.vault

import mesosphere.marathon.plugin.RunSpec
import mesosphere.marathon.plugin.plugin.PluginConfiguration
import mesosphere.marathon.plugin.task.RunSpecTaskProcessor
import org.apache.mesos.Protos
import org.apache.mesos.Protos.TaskInfo
import org.slf4j.LoggerFactory
import play.api.libs.json.JsObject

class VaultPlugin extends RunSpecTaskProcessor with PluginConfiguration {

  private val logger = LoggerFactory.getLogger(classOf[VaultPlugin])
  logger.info("Vault plugin instantiated")

  override def apply(runSpec: RunSpec, builder: TaskInfo.Builder): Unit = {
    val envBuilder = builder.getCommand.getEnvironment.toBuilder

    envBuilder.addVariables(Protos.Environment.Variable.newBuilder()
      .setName("TESTVAR")
      .setValue("TESTVALUE"))

    val commandBuilder = builder.getCommand.toBuilder
    commandBuilder.setEnvironment(envBuilder)
    builder.setCommand(commandBuilder)
  }

  override def initialize(marathonInfo: Map[String, Any], configuration: JsObject): Unit = {

  }
}

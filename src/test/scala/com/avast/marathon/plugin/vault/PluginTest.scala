package com.avast.marathon.plugin.vault

import java.util.concurrent.TimeUnit

import com.bettercloud.vault.{Vault, VaultConfig}
import mesosphere.marathon.client.MarathonClient
import org.junit.runner.RunWith
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.junit.JUnitRunner

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class PluginTest extends FlatSpec with Matchers {

  private lazy val marathonUrl = s"http://${System.getProperty("marathon.host")}:${System.getProperty("marathon.tcp.8080")}"
  private lazy val mesosSlaveUrl = s"http://${System.getProperty("mesos-slave.host")}:${System.getProperty("mesos-slave.tcp.5051")}"
  private lazy val vaultUrl = s"http://${System.getProperty("vault.host")}:${System.getProperty("vault.tcp.8200")}"

  it should "read existing secret" in {
    check("SECRETVAR", deployWithSecret) { envVarValue =>
      envVarValue shouldBe "testValue"
    }
  }

  private def deployWithSecret(envVarName: String): String = {
    val appId = "testappjson"
    val json = s"""{ "id": "$appId","cmd": "${EnvAppCmd.create(envVarName)}","env": {"$envVarName": {"secret": "pwd"}},"secrets": {"pwd": {"source": "secret/test@testKey"}}}"""

    val marathonResponse = new MarathonClient(marathonUrl).put("testappjson", json)
    appId
  }

  private def check(envVarName: String, deployApp: String => String)(verifier: String => Unit) = {

    val client = MarathonClient.getInstance(marathonUrl)
    val eventStream = new MarathonEventStream(marathonUrl)

    val vaultConfig = new VaultConfig().address(vaultUrl).token("testroottoken").build()
    val vault = new Vault(vaultConfig)
    vault.logical().write("secret/test", Map[String, AnyRef]("testKey" -> "testValue").asJava)

    val appId = deployApp(envVarName)
    val appCreatedFuture = eventStream.when(_.eventType.contains("deployment_success"))
    Await.result(appCreatedFuture, Duration.create(20, TimeUnit.SECONDS))

    val agentClient = MesosAgentClient(mesosSlaveUrl)
    val state = agentClient.fetchState()

    val envVarValue = agentClient.waitForStdOutContentsMatch(envVarName, state.frameworks(0).executors(0),
      o => EnvAppCmd.extractEnvValue(envVarName, o),
      java.time.Duration.ofSeconds(30))

    verifier(envVarValue)

    client.deleteApp(appId)

    val appRemovedFuture = eventStream.when(_.eventType.contains("deployment_success"))
    Await.result(appRemovedFuture, Duration.create(20, TimeUnit.SECONDS))

    eventStream.close()
  }
}

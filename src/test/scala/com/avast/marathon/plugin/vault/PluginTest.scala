package com.avast.marathon.plugin.vault

import java.util.concurrent.TimeUnit

import mesosphere.marathon.client.MarathonClient
import mesosphere.marathon.client.model.v2.App
import org.junit.runner.RunWith
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.junit.JUnitRunner

import scala.concurrent.Await
import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class PluginTest extends FlatSpec with Matchers {
  it should "modify environment variables" in {

    val marathonUrl = s"http://${System.getProperty("marathon.host")}:${System.getProperty("marathon.tcp.8080")}"
    val mesosSlaveUrl = s"http://${System.getProperty("mesos-slave.host")}:${System.getProperty("mesos-slave.tcp.5051")}"
    val vaultUrl = s"http://${System.getProperty("vault.host")}:${System.getProperty("vault.tcp.8200")}"

    val client = MarathonClient.getInstance(marathonUrl)
    val eventStream = new MarathonEventStream(marathonUrl)

    val app = new App
    app.setId("testapp")
    app.setInstances(1)
    val cmd = EnvAppCmd.create("TESTVAR")
    app.setCmd(cmd)

    val appCreatedFuture = eventStream.when(_.eventType.contains("deployment_success"))
    val result = client.createApp(app)
    Await.result(appCreatedFuture, Duration.create(20, TimeUnit.SECONDS))

    val agentClient = MesosAgentClient(mesosSlaveUrl)
    val state = agentClient.fetchState()

    val envVarValue = agentClient.waitForStdOutContentsMatch(state.frameworks(0).executors(0),
      o => EnvAppCmd.extractEnvValue("TESTVAR", o),
      java.time.Duration.ofSeconds(30))

    envVarValue shouldBe "TESTVALUE"

    client.deleteApp(result.getId)

    val appRemovedFuture = eventStream.when(_.eventType.contains("deployment_success"))
    Await.result(appRemovedFuture, Duration.create(20, TimeUnit.SECONDS))

    eventStream.close()
  }
}

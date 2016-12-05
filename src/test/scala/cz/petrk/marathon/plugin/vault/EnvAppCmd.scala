package cz.petrk.marathon.plugin.vault

object EnvAppCmd {
  def create(envName: String): String = {
    "echo START && sleep 3 && echo ENV %s: ${%s} && sleep 5000".format(envName, envName)
  }

  def extractEnvValue(envName: String, stdOut: String): Option[String] = {
    val regEx = ("(?m)^ENV " + envName + ": (.*?)$").r
    val m = regEx.findFirstMatchIn(stdOut)
    m.map(_.group(1))
  }
}

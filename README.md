# Marathon Vault plugin [![Build Status](https://travis-ci.org/avast/marathon-vault-plugin.svg?branch=master)](https://travis-ci.org/avast/marathon-vault-plugin) [![Download](https://api.bintray.com/packages/avast/maven/marathon-vault-plugin/images/download.svg) ](https://bintray.com/avast/maven/marathon-vault-plugin/_latestVersion)

Plugin for [Marathon](https://mesosphere.github.io/marathon/) which injects secrets stored in [Vault](https://www.vaultproject.io/) via environment variables.

## How to reference secrets in marathon.json

The following example `marathon.json` fragment will read Vault path `/secret/shared/abc/xyz`, extract field `password` from that path and inject the field value into an environment variable named `ENV_NAME`:

```json
{
  "env": {
    "ENV_NAME": {
      "secret": "secret_ref"
    }
  },
  "secrets": {
    "secret_ref": {
      "source": "/abc/xyz@password"
    }
  }
}
```

If the provided Vault path or field is not found, the environment variable will not be set. The same applies when it cannot be read because of permissions or other types of errors. Either way, it will be logged as an error in Marathon logs.

The path in the secret source can be absolute or relative and it depends on format of the secret source. The path is absolute if the secret source starts with `/` otherwise is relative path.
Both paths have a root defined in configuration (`sharedRoot` for absolute path and `appSpecificRoot` for relative path). 

### Absolute path to a secret

For the absolute path is a path to the vault defined as `<sharedRoot>/<path from the secret source>` (e.g. `secret/shared/database@password`). This kind of secret allows you to share secrets between applications.

### Relative path to a secret

For the relative path is a path to the vault defined as `<appSpecificRoot>/<marathon path and service name>/<path from the secret source>` (e.g. for application `test/myTestApp` it is `secret/private/test/myTestApp/database@password`). This concept will guarantee that secrets can not be read from other applications.

## Installation

Please consult the [Start Marathon with plugins](https://mesosphere.github.io/marathon/docs/plugin.html#start-marathon-with-plugins) section of the official docs for a general overview of how plugins are enabled.

The plugin configuration JSON file will need to reference the Vault plugin as follows:

```json
{
  "plugins": {
    "envVarExtender": {
      "plugin": "mesosphere.marathon.plugin.task.RunSpecTaskProcessor",
      "implementation": "com.avast.marathon.plugin.vault.VaultPlugin",
      "configuration": {
        "address": "http://address_to_your_vault_instance:port",
        "token": "access_token",
        "pathProvider": {
          "sharedRoot": "secret/shared/",
          "appSpecificRoot": "secret/private/"
        }
      }
    }
  }
}
```

You will also need to start Marathon with the secrets feature being enabled. See [Marathon command line flags](https://mesosphere.github.io/marathon/docs/command-line-flags) for more details. In short, it can be enabled by
* specifying `--enable_features secrets` in Marathon command line
* specifying environment variable `MARATHON_ENABLE_FEATURES=secrets` when starting Marathon

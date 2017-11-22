# Marathon Vault plugin [![Download](https://api.bintray.com/packages/avast/maven/marathon-vault-plugin/images/download.svg) ](https://bintray.com/avast/maven/marathon-vault-plugin/_latestVersion) [![release](http://github-release-version.herokuapp.com/github/avast/marathon-vault-plugin/release.svg?style=flat)](https://github.com/avast/marathon-vault-plugin/releases/latest)

| Marathon version | v1.4.1            | v1.4.5            | v1.5.0            | latest            |
| ---------------- |-------------------|-------------------|-------------------| ------------------|
| Build status     | [![Build1][1]][5] | [![Build2][2]][5] | [![Build3][3]][5] | [![Build4][4]][5] |

[1]: https://travis-matrix-badges.herokuapp.com/repos/avast/marathon-vault-plugin/branches/master/1
[2]: https://travis-matrix-badges.herokuapp.com/repos/avast/marathon-vault-plugin/branches/master/2
[3]: https://travis-matrix-badges.herokuapp.com/repos/avast/marathon-vault-plugin/branches/master/3
[4]: https://travis-matrix-badges.herokuapp.com/repos/avast/marathon-vault-plugin/branches/master/4
[5]: https://travis-ci.org/avast/marathon-vault-plugin

Plugin for [Marathon](https://mesosphere.github.io/marathon/) which injects secrets stored in [Vault](https://www.vaultproject.io/) via environment variables.

## How to reference secrets in marathon.json

The following example `marathon.json` fragment will read Vault path `secret/shared/abc/xyz` (`secret/shared` is taken from the configuration), extract field `password` from that path and inject the field value into an environment variable named `ENV_NAME`:

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

The path in the secret source can be "shared" or "app-private" and it depends on the secret source format. The path is shared if the secret source starts with `/`, otherwise it is a private path.
Both paths have a root defined in configuration (`sharedPathRoot` for shared path and `privatePathRoot` for private path). 

### Shared path to a secret

For a shared secret source path, a Vault path is constructed as `<sharedPathRoot>/<path from the secret source>`. E.g., with `secret/shared` as a value configured in `sharedPathRoot`, and `/abc/xyz@password` as the secret source, the resulting Vault path will be `secret/shared/abc/xyz`, field name `password`. This kind of secret reference allows you to share secrets between applications.

### Private path to a secret

For a private secret source path, a Vault path is constructed as `<privatePathRoot>/<marathon path and service name>/<path from the secret source>`. E.g., with `secret/marathon` as a value configured in `privatePathRoot`, and `abc/xyz@password` as the secret source of an application with Marathon id `test/test-app`, the resulting Vault path will be `secret/marathon/test/test-app/abc/xyz`, field name `password`. This concept will guarantee that secrets cannot be read from other applications, but on the other hand identical secrets will need to be stored multiple times in Vault (separately for each Marathon application).

## Installation

Please consult the [Start Marathon with plugins](https://mesosphere.github.io/marathon/docs/plugin.html#start-marathon-with-plugins) section of the official docs for a general overview of how plugins are enabled.

The plugin configuration JSON file will need to reference the Vault plugin as follows:

```json
{
  "plugins": {
    "marathon-vault-plugin": {
      "plugin": "mesosphere.marathon.plugin.task.RunSpecTaskProcessor",
      "implementation": "com.avast.marathon.plugin.vault.VaultPlugin",
      "configuration": {
        "address": "http://address_to_your_vault_instance:port",
        "token": "access_token",
        "sharedPathRoot": "secret/shared/",
        "privatePathRoot": "secret/private/"
      }
    }
  }
}
```

Properties `sharedPathRoot` and `privatePathRoot` are optional. Default value for both properties is root (which means `/`).

In this version, only token-based login is supported. Never use the Vault's initial root token - we recommend creating a separate token with long-enough validity and restricted (read-only) access to secrets.

You will also need to start Marathon with the secrets feature being enabled. See [Marathon command line flags](https://mesosphere.github.io/marathon/docs/command-line-flags) for more details. In short, it can be enabled by
* specifying `--enable_features secrets` in Marathon command line
* specifying environment variable `MARATHON_ENABLE_FEATURES=secrets` when starting Marathon

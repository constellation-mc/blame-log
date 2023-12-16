# <img src="https://github.com/melontini/blame-log/assets/104443436/5337785e-0377-41da-9ede-e46344768e42" width="75" height="75"> BlameLog

> Blaming logs since 2023.

### What's this?

This is a proof-of-concept mod, which patches Loggers to prepend method callers.

Wondering which mod printed this?

```
[02:16:21] [Render thread/INFO]: Trying to read config file...
[02:16:21] [Render thread/INFO]: A config file was found, loading it..
[02:16:21] [Render thread/INFO]: Successfully loaded config file.
```

Well, now you know!

```
[02:16:21] [Render thread/INFO]: [me.luligabi.noindium.NoIndium#createConfig] Trying to read config file...
[02:16:21] [Render thread/INFO]: [me.luligabi.noindium.NoIndium#createConfig] A config file was found, loading it..
[02:16:21] [Render thread/INFO]: [me.luligabi.noindium.NoIndium#createConfig] Successfully loaded config file.
```


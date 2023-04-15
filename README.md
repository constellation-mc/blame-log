# <img src="https://user-images.githubusercontent.com/104443436/232251382-9500aee5-30d4-46d3-b98d-c0a88f5caff1.png" width="75" height="75"> BlameLog

> Blaming logs since 2023.

### What's this?

This is a proof-of-concept mod, which patches Loggers to append method callers.

Wandering which mod printed this?

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

### How does this work?

This uses InstrumentationAccess from [CrackerUtil's Danger](https://github.com/melontini/cracker-util/wiki/Danger) to patch AbstractLogger and Log4jLogger.

Note that this mod tries to dig deeper into the stack if it detects Logger classes.
The logic behind this is super simple.

```java
   while (StringUtils.containsAnyIgnoreCase(name, "log4j", "slf4j", "logger") || StringUtils.endsWithAny(name.split("#")[0], "Logger", "Log", "LogHelper", "LoggerAdapterAbstract")) {
      depth++;
      name = getCallerName(depth);
   }
```

So expect false-positives, like mmm BlameLog...

See the only class in this mod for more info.
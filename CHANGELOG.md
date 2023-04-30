## What's new:

* Better Mixin handling. Now `net.minecraft` is replaced by the mixin class if the method was injected.
* * These can get hilariously long: 
* * `[de.keksuccino.drippyloadingscreen.mixin.mixins.client.MixinLoadingOverlay#handler$enb000$drippyloadingscreen$onConstruct]`
* * `[net.mehvahdjukaar.supplementaries.mixins.SheetsClassloadingFixHackMixin#handler$mnf000$supplementaries$whyDoIHaveToDoThis]`
* Added 2 more checks, 1. If class name ends with `Logging` 2. If a method is called `log`.
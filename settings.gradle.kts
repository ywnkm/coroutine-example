
rootProject.name = "coroutine-example"

include(":http-echo-server")

val enableNative = settings.extra.has("enableNative") && "true" == settings.extra.get("enableNative")

if (enableNative) {
    include(":http-echo-server-kn-uring")
}


plugins {
    kotlin("multiplatform")
}

kotlin {

    linuxX64 {

        compilations.getByName("main") {
            cinterops {

                val liburing by creating {
                    definitionFile.set(project.file("src/nativeInterop/cinterop/liburing.def"))
                    packageName("liburing")
                    includeDirs {
                        this.allHeaders(project.file("src/nativeInterop/cinterop/liburing/include").absolutePath)
                    }
                }

            }
        }

        binaries {
            executable {
                entryPoint = "main"
                linkerOpts("-L${project.file("src/nativeInterop/cinterop/liburing").absolutePath}", "-luring-ffi")
            }
        }
    }
}
